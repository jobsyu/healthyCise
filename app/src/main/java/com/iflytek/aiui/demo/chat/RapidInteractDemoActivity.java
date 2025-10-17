package com.iflytek.aiui.demo.chat;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.iflytek.aiui.AIUIAgent;
import com.iflytek.aiui.AIUIConstant;
import com.iflytek.aiui.AIUIEvent;
import com.iflytek.aiui.AIUIListener;
import com.iflytek.aiui.AIUIMessage;
import com.iflytek.aiui.AIUISetting;
import com.iflytek.aiui.Version;
import com.iflytek.aiui.demo.chat.utils.DeviceUtil;
import com.iflytek.aiui.demo.chat.utils.FucUtil;
import com.iflytek.aiui.demo.chat.utils.PermissionUtil;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 极速交互demo。
 */
public class RapidInteractDemoActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "RapidInteractDemo";
    private static final int REQUEST_CODE = 12345;

    private Toast mToast;
    private TextView mTimeSpentText;
    private EditText mNlpText;

    private AIUIAgent mAIUIAgent = null;
    private boolean mIsWakeupEnable = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rapid_interact_demo);

        initUI();

        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        audioManager.setMode(AudioManager.MODE_IN_CALL);
        audioManager.setSpeakerphoneOn(true);
    }

    private void initUI() {
        mToast = Toast.makeText(this, "", Toast.LENGTH_SHORT);

        findViewById(R.id.nlp_create).setOnClickListener(RapidInteractDemoActivity.this);
        findViewById(R.id.nlp_destroy).setOnClickListener(RapidInteractDemoActivity.this);
        findViewById(R.id.nlp_start).setOnClickListener(RapidInteractDemoActivity.this);
        findViewById(R.id.nlp_stop_rec).setOnClickListener(RapidInteractDemoActivity.this);

        mTimeSpentText = findViewById(R.id.txt_time_spent);
        mNlpText = findViewById(R.id.nlp_text);
        mNlpText.append("sdk_ver: " + Version.getVersion());
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            // 创建AIUIAgent
            case R.id.nlp_create:
                createAgent();
                break;

            // 销毁AIUIAgent
            case R.id.nlp_destroy:
                destroyAgent();
                break;

            // 开始语音语义
            case R.id.nlp_start:
                startVoiceNlp();
                break;

            // 停止语音语义
            case R.id.nlp_stop_rec:
                stopVoiceNlp();
                break;

            default:
                break;
        }
    }

    private String getAIUIParams() {
        String params = FucUtil.readAssetFile(this,
                "cfg/aiui_phone.cfg", "utf-8");

        try {
            JSONObject paramsJson = new JSONObject(params);

            mIsWakeupEnable = !"off".equals(paramsJson.optJSONObject("speech").optString(
                    "wakeup_mode"));
            if (mIsWakeupEnable) {
                FucUtil.copyAssetFolder(this, "ivw", "/sdcard/AIUI/ivw");
            }

            params = paramsJson.toString();
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return params;
    }

    private void createAgent() {
        if (checkCallingOrSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            PermissionUtil.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_CODE);
            return;
        }

        if (null == mAIUIAgent) {
            Log.i(TAG, "createAgent");

            // 为每一个设备设置对应唯一的SN（最好使用设备硬件信息(mac地址，设备序列号等）生成），以便正确统计装机量，避免刷机或者应用卸载重装导致装机量重复计数
            String deviceId = DeviceUtil.getDeviceId(this);
            Log.i(TAG, "deviceId=" + deviceId);

            AIUISetting.setNetLogLevel(AIUISetting.LogLevel.debug);
            AIUISetting.setSystemInfo(AIUIConstant.KEY_SERIAL_NUM, deviceId);

            // 6.6.xxxx.xxxx及以上版本SDK设置用户唯一标识uid（可选，AIUI后台服务需要，不设置则会使用上面的deviceId作为uid）
            // 5.6.xxxx.xxxx版本SDK不能也不需要设置uid
            // AIUISetting.setSystemInfo(AIUIConstant.KEY_UID, deviceId);

            mAIUIAgent = AIUIAgent.createAgent(this, getAIUIParams(), mAIUIListener);
        }

        if (null == mAIUIAgent) {
            final String strErrorTip = "创建AIUIAgent失败！";
            showTip(strErrorTip);

            mNlpText.setText(strErrorTip);
        } else {
            showTip("AIUIAgent已创建");
        }
    }

    private void destroyAgent() {
        if (null != mAIUIAgent) {
            Log.i(TAG, "destroyAgent");

            mAIUIAgent.destroy();
            mAIUIAgent = null;

            showTip("AIUIAgent已销毁");
        }
    }

    private void startVoiceNlp() {
        if (null == mAIUIAgent) {
            showTip("AIUIAgent为空，请先创建");
            return;
        }

        if (checkCallingOrSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            PermissionUtil.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    REQUEST_CODE);
            return;
        }

        Log.i(TAG, "startVoiceNlp");

        mNlpText.setText("");

        // 先发送唤醒消息，改变AIUI内部状态，只有唤醒状态才能接收语音输入
        // 默认为oneshot模式，即一次唤醒后就进入休眠。可以修改aiui_phone.cfg中speech参数的interact_mode为continuous以支持持续交互
        if (!mIsWakeupEnable) {
            AIUIMessage wakeupMsg = new AIUIMessage(AIUIConstant.CMD_WAKEUP, 0, 0, "", null);
            mAIUIAgent.sendMessage(wakeupMsg);
        }

        // 打开AIUI内部录音机，开始录音。若要使用上传的个性化资源增强识别效果，则在参数中添加pers_param设置
        // 个性化资源使用方法可参见http://doc.xfyun.cn/aiui_mobile/的用户个性化章节
        // 在输入参数中设置tag，则对应结果中也将携带该tag，可用于关联输入输出
        String params = "sample_rate=16000,data_type=audio,pers_param={\"uid\":\"\"},tag=audio-tag";
        AIUIMessage startRecord = new AIUIMessage(AIUIConstant.CMD_START_RECORD, 0, 0, params,
                null);

        mAIUIAgent.sendMessage(startRecord);
    }

    private void stopVoiceNlp() {
        if (null == mAIUIAgent) {
            showTip("AIUIAgent 为空，请先创建");
            return;
        }

        Log.i(TAG, "stopVoiceNlp");

        // 停止录音
        String params = "sample_rate=16000,data_type=audio";
        AIUIMessage stopRecord = new AIUIMessage(AIUIConstant.CMD_STOP_RECORD, 0, 0, params, null);

        mAIUIAgent.sendMessage(stopRecord);
    }

    private String mCurTtsSid = "";

    private boolean mIsValidTTSAudioArrived;

    private final AIUIListener mAIUIListener = new AIUIListener() {
        @Override
        public void onEvent(AIUIEvent event) {
            Log.i(TAG, "onEvent, eventType=" + event.eventType);

            switch (event.eventType) {
                case AIUIConstant.EVENT_CONNECTED_TO_SERVER:
                    showTip("已连接服务器");
                    break;

                case AIUIConstant.EVENT_SERVER_DISCONNECTED:
                    showTip("与服务器断连");
                    break;

                case AIUIConstant.EVENT_WAKEUP:
                    showTip("进入识别状态");
                    break;

                case AIUIConstant.EVENT_RESULT: {
                    try {
                        JSONObject bizParamJson = new JSONObject(event.info);
                        JSONObject data = bizParamJson.getJSONArray("data").getJSONObject(0);
                        JSONObject params = data.getJSONObject("params");
                        JSONObject content = data.getJSONArray("content").getJSONObject(0);
                        String sub = params.optString("sub");

                        // 获取该路会话的id，将其提供给支持人员，有助于问题排查
                        // 也可以从Json结果中看到
                        String sid = event.data.getString("sid");
                        String tag = event.data.getString("tag");

                        if (content.has("cnt_id") && !"tts".equals(sub)) {
                            String cnt_id = content.getString("cnt_id");
                            String cntStr = new String(event.data.getByteArray(cnt_id), "utf-8");

                            // 获取从数据发送完到获取结果的耗时，单位：ms
                            // 也可以通过键名"bos_rslt"获取从开始发送数据到获取结果的耗时
                            long eosRsltTime = event.data.getLong("eos_rslt", -1);
                            mTimeSpentText.setText(sub + ":" + eosRsltTime + "ms");

                            if (TextUtils.isEmpty(cntStr)) {
                                return;
                            }

                            JSONObject cntJson = new JSONObject(cntStr);

                            if (mNlpText.getLineCount() > 1000) {
                                mNlpText.setText("");
                            }

                            mNlpText.append("\n");
                            mNlpText.append(cntJson.toString());
                            mNlpText.setSelection(mNlpText.getText().length());

                            if ("nlp".equals(sub)) {
                                // 解析得到语义结果
                                String resultStr = cntJson.optString("intent");
                                Log.i(TAG, resultStr);
                            }

                            mNlpText.append("\n");
                        }

                        if ("tts".equals(sub)) {
                            if (!mCurTtsSid.equals(sid)) {
                                mCurTtsSid = sid;
                                mIsValidTTSAudioArrived = false;
                            }

                            int dts = content.getInt("dts");
                            String cnt_id = content.getString("cnt_id");
                            byte[] audio = event.data.getByteArray(cnt_id);

                            assert audio != null;
                            if (audio.length > 0) {
                                if (!mIsValidTTSAudioArrived) {
                                    mIsValidTTSAudioArrived = true;

                                    long eosRsltTime = event.data.getLong("eos_rslt", -1);
                                    mTimeSpentText.setText(sub + ":" + eosRsltTime + "ms");
                                }
                            }
                        }
                    } catch (Throwable e) {
                        e.printStackTrace();
                        mNlpText.append("\n");
                        mNlpText.append(e.getLocalizedMessage());
                    }

                }
                break;

                case AIUIConstant.EVENT_ERROR: {
                    mNlpText.append("\n");
                    mNlpText.append("错误: " + event.arg1 + "\n" + event.info);
                }
                break;

                case AIUIConstant.EVENT_VAD: {
                    if (AIUIConstant.VAD_BOS == event.arg1) {
                        showTip("找到vad_bos");
                    } else if (AIUIConstant.VAD_EOS == event.arg1) {
                        showTip("找到vad_eos");
                    } else if (AIUIConstant.VAD_VOL == event.arg1) {
                        showTip("" + event.arg2);
                    }
                }
                break;

                case AIUIConstant.EVENT_START_RECORD: {
                    showTip("已开始录音");
                }
                break;

                case AIUIConstant.EVENT_STOP_RECORD: {
                    showTip("已停止录音");
                }
                break;

                case AIUIConstant.EVENT_STATE: {    // 状态事件
                    int state = event.arg1;

                    if (AIUIConstant.STATE_IDLE == state) {
                        // 闲置状态，AIUI未开启
                        showTip("STATE_IDLE");
                    } else if (AIUIConstant.STATE_READY == state) {
                        // AIUI已就绪，等待唤醒
                        showTip("STATE_READY");
                    } else if (AIUIConstant.STATE_WORKING == state) {
                        // AIUI工作中，可进行交互
                        showTip("STATE_WORKING");
                    }
                }
                break;

                default:
                    break;
            }
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (null != mAIUIAgent) {
            mAIUIAgent.destroy();
            mAIUIAgent = null;
        }
    }

    private void showTip(final String str) {
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                mToast.setText(str);
                mToast.show();
            }
        });
    }
}
