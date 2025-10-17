package com.iflytek.aiui.demo.chat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.PermissionChecker;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import com.iflytek.aiui.AIUIAgent;
import com.iflytek.aiui.AIUIConstant;
import com.iflytek.aiui.AIUIEvent;
import com.iflytek.aiui.AIUIListener;
import com.iflytek.aiui.AIUIMessage;
import com.iflytek.aiui.AIUISetting;
import com.iflytek.aiui.demo.chat.ui.ToggleImageButton;
import com.iflytek.aiui.demo.chat.ui.VoiceIndicator;
import com.iflytek.aiui.demo.chat.utils.DeviceUtil;
import com.iflytek.aiui.demo.chat.utils.FucUtil;
import com.iflytek.aiui.demo.chat.utils.PermissionUtil;
import com.iflytek.aiui.demo.chat.utils.iat.IatResultHelper;
import com.iflytek.aiui.demo.chat.utils.nlp.NlpResultHelper;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * 极速交互demo。
 */
public class RapidInteractDemoActivity2 extends AppCompatActivity {
    private static final String TAG = "RapidInteractDemo";
    private static final int REQUEST_CODE = 12345;

    private Toast mToast;
    private ConstraintLayout mMiddleLayout;
    private RecyclerView mChatRecyclerView;
    private Button mClearRecordsButton;

    private ConstraintLayout mTopLayout;
    private Switch mSubtitleSwitch;
    private VoiceIndicator mFeifeiIndicator;
    private ScrollView mCurChatView;
    private TextView mUserWordsText;
    private TextView mFeifeiAnsText;
    private ToggleImageButton mCallButton;
    private ToggleImageButton mMicButton;
    private LinearLayout mUserIndicatorLayout;
    private VoiceIndicator mUserIndicator;

    private AIUIAgent mAIUIAgent = null;
    private boolean mIsWakeupEnable = false;

    private enum CallState {
        INIT,
        TALKING
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rapid_interact_demo2);

        mMockThread = new HandlerThread("mock_vol");
        mMockThread.start();
        mMockHandler = new MockHandler(mMockThread.getLooper());

        initUI();
        createAgent();

        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        audioManager.setMode(AudioManager.MODE_IN_CALL);
        audioManager.setSpeakerphoneOn(true);
    }

    @SuppressLint("NotifyDataSetChanged")
    private void initUI() {
        mToast = Toast.makeText(this, "", Toast.LENGTH_SHORT);

        mMiddleLayout = findViewById(R.id.clyt_middle);
        mChatRecyclerView = findViewById(R.id.rcyc_chat);
        mClearRecordsButton = findViewById(R.id.btn_clear_records);

        mTopLayout = findViewById(R.id.clyt_top);
        mSubtitleSwitch = findViewById(R.id.swt_subtitle);
        mFeifeiIndicator = findViewById(R.id.vi_feifei);
        mCurChatView = findViewById(R.id.scv_cur_chat);
        mUserWordsText = findViewById(R.id.txt_user_words);
        mFeifeiAnsText = findViewById(R.id.txt_feifei_ans);
        mCallButton = findViewById(R.id.tib_call);
        mMicButton = findViewById(R.id.tib_mic);
        mUserIndicatorLayout = findViewById(R.id.llyt_user_voice_indicator);
        mUserIndicator = findViewById(R.id.vi_user);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        mChatRecyclerView.setLayoutManager(layoutManager);
        mChatRecyclerView.setOverScrollMode(RecyclerView.OVER_SCROLL_NEVER);

        // 设置成false更新item内容时才不会闪
        ((SimpleItemAnimator) Objects.requireNonNull(mChatRecyclerView.getItemAnimator()))
                .setSupportsChangeAnimations(false);
        mChatRecyclerView.setAdapter(mChatAdapter);

        mClearRecordsButton.setOnClickListener(v -> {
            mChatAdapter.clear();
            mChatAdapter.notifyDataSetChanged();
        });

        int height = mFeifeiIndicator.getLayoutParams().height;
        LinearGradient linearGradient = new LinearGradient(0, 0, 0, height,
                Color.parseColor("#FF9172FE"),
                Color.parseColor("#FFFFFFFF"), Shader.TileMode.CLAMP);
        mFeifeiIndicator.setBarColor(linearGradient);

        mCallButton.setDrawables(R.drawable.ic_call, R.drawable.ic_call_hungup);
        mCallButton.setIsChecked(true);
        mCallButton.setOnToggleListener(new ToggleImageButton.OnToggleListener() {
            @Override
            public boolean onClick(boolean isChecked) {
                if (isChecked) {
                    boolean ret = startVoiceNlp();
                    if (ret) {
                        mIgnoreTag = "ignore_this_iat";
                        sendTextNlp("向用户问好", mIgnoreTag);

                        mCurCallState = CallState.TALKING;
                        setUIStatus(mCurCallState);
                    }

                    return ret;
                } else {
                    cancelTTS();
                    stopVoiceNlp();
                    mCurCallState = CallState.INIT;
                    setUIStatus(mCurCallState);

                    return true;
                }
            }
        });

        mMicButton.setDrawables(R.drawable.ic_mic_normal, R.drawable.ic_mic_mute);
        mMicButton.setIsChecked(true);
        mMicButton.setOnToggleListener(new ToggleImageButton.OnToggleListener() {
            @Override
            public boolean onClick(boolean isChecked) {
                if (isChecked) {
                    stopVoiceNlp();

                    return true;
                } else {
                    return startVoiceNlp();
                }
            }
        });

        mSubtitleSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mCurChatView.setVisibility(isChecked ? View.VISIBLE : View.INVISIBLE);
            }
        });

        setUIStatus(mCurCallState);
    }

    private CallState mCurCallState = CallState.INIT;

    private void setUIStatus(CallState status) {
        switch (status) {
            case INIT: {
                mMiddleLayout.setVisibility(View.VISIBLE);
                mTopLayout.setVisibility(View.INVISIBLE);
                mUserIndicatorLayout.setVisibility(View.GONE);
                mMicButton.setVisibility(View.GONE);
            } break;

            case TALKING: {
                mMiddleLayout.setVisibility(View.INVISIBLE);
                mTopLayout.setVisibility(View.VISIBLE);
                mUserIndicatorLayout.setVisibility(View.VISIBLE);
                mMicButton.setVisibility(View.VISIBLE);
                mMicButton.setIsChecked(true);
                mUserWordsText.setText("");
                mFeifeiAnsText.setText("");
            } break;
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

    private boolean createAgent() {
        if (checkCallingOrSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            PermissionUtil.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_CODE);
            return false;
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

        return true;
    }

    private void destroyAgent() {
        if (null != mAIUIAgent) {
            Log.i(TAG, "destroyAgent");

            mAIUIAgent.destroy();
            mAIUIAgent = null;
        }
    }

    private String mIgnoreTag = "1234";

    private boolean sendTextNlp(String text, String tag) {
        if (null == mAIUIAgent) {
            showTip("AIUIAgent为空，请先创建");
            return false;
        }

        // 先发送唤醒消息，改变AIUI内部状态，只有唤醒状态才能接收语音输入
        // 默认为oneshot模式，即一次唤醒后就进入休眠。可以修改aiui_phone.cfg中speech参数的interact_mode为continuous以支持持续交互
        if (!mIsWakeupEnable) {
            AIUIMessage wakeup = new AIUIMessage(AIUIConstant.CMD_WAKEUP, 0, 0, "", null);
            mAIUIAgent.sendMessage(wakeup);
        }

        AIUIMessage writeText = new AIUIMessage(AIUIConstant.CMD_WRITE, 0,
                0, "data_type=text,tag=" + tag, text.getBytes());
        mAIUIAgent.sendMessage(writeText);

        return true;
    }

    private boolean startVoiceNlp() {
        if (null == mAIUIAgent) {
            showTip("AIUIAgent为空，请先创建");
            return false;
        }

        if (checkCallingOrSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            PermissionUtil.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    REQUEST_CODE);
            return false;
        }

        Log.i(TAG, "startVoiceNlp");

        // 先发送唤醒消息，改变AIUI内部状态，只有唤醒状态才能接收语音输入
        // 默认为oneshot模式，即一次唤醒后就进入休眠。可以修改aiui_phone.cfg中speech参数的interact_mode为continuous以支持持续交互
        if (!mIsWakeupEnable) {
            AIUIMessage wakeup = new AIUIMessage(AIUIConstant.CMD_WAKEUP, 0, 0, "", null);
            mAIUIAgent.sendMessage(wakeup);
        }

        // 打开AIUI内部录音机，开始录音。若要使用上传的个性化资源增强识别效果，则在参数中添加pers_param设置
        // 个性化资源使用方法可参见http://doc.xfyun.cn/aiui_mobile/的用户个性化章节
        // 在输入参数中设置tag，则对应结果中也将携带该tag，可用于关联输入输出
        String params = "sample_rate=16000,data_type=audio,pers_param={\"uid\":\"\"},tag=audio-tag";
        AIUIMessage startRecord = new AIUIMessage(AIUIConstant.CMD_START_RECORD, 0, 0, params,
                null);

        mAIUIAgent.sendMessage(startRecord);

        return true;
    }

    private void cancelTTS() {
        if (null == mAIUIAgent) {
            showTip("AIUIAgent 为空，请先创建");
            return;
        }

        Log.i(TAG, "cancelTTS");

        AIUIMessage cancelTTS = new AIUIMessage(AIUIConstant.CMD_TTS, AIUIConstant.CANCEL,
                0, "", null);

        mAIUIAgent.sendMessage(cancelTTS);

        mMockHandler.sendEmptyMessage(MSG_MOCK_CLEAR);
    }

    private void stopVoiceNlp() {
        if (null == mAIUIAgent) {
            showTip("AIUIAgent 为空，请先创建");
            return;
        }

        Log.i(TAG, "stopVoiceNlp");

        AIUIMessage resetWakeup = new AIUIMessage(AIUIConstant.CMD_RESET_WAKEUP, 0, 0, "", null);
        mAIUIAgent.sendMessage(resetWakeup);

        // 停止录音
        String params = "sample_rate=16000,data_type=audio";
        AIUIMessage stopRecord = new AIUIMessage(AIUIConstant.CMD_STOP_RECORD, 0, 0, params, null);

        mAIUIAgent.sendMessage(stopRecord);
    }

    private String mCurIatSid = "";

    private String mCurNlpSid = "";

    private final IatResultHelper mIatResultHelper = new IatResultHelper();

    private final NlpResultHelper mNlpResultHelper = new NlpResultHelper();

    private final AIUIListener mAIUIListener = new AIUIListener() {
        @Override
        public void onEvent(AIUIEvent event) {
            Log.i(TAG, "onEvent, eventType=" + event.eventType);

            switch (event.eventType) {
                case AIUIConstant.EVENT_CONNECTED_TO_SERVER:
                    break;

                case AIUIConstant.EVENT_SERVER_DISCONNECTED:
                    break;

                case AIUIConstant.EVENT_WAKEUP:
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
                        String sid = event.data.getString("sid", "");
                        String tag = event.data.getString("tag", "");

                        if (mIgnoreTag.equals(tag) && "iat".equals(sub)) {
                            // 过滤掉首次模拟交互的iat结果
                            mIgnoreTag = "1234";
                            return;
                        }

                        if (content.has("cnt_id") && !"tts".equals(sub)) {
                            String cnt_id = content.getString("cnt_id");
                            String cntStr = new String(event.data.getByteArray(cnt_id), "utf-8");

                            if (TextUtils.isEmpty(cntStr)) {
                                return;
                            }

                            JSONObject cntJson = new JSONObject(cntStr);
                            if ("iat".equals(sub)) {
                                if (!sid.equals(mCurIatSid)) {
                                    mCurIatSid = sid;
                                    mIatResultHelper.clear();
                                    mFeifeiAnsText.setText("");
                                }

                                String iatText = mIatResultHelper.processIATResult(cntJson);
                                mUserWordsText.setText(iatText);

                                if (mIatResultHelper.isLastResult()) {
                                    int pos = mChatAdapter.add(new ChatItem(ItemType.USER,
                                            iatText));
                                    mChatAdapter.notifyItemRangeInserted(pos, 1);
                                }
                            } else if ("nlp".equals(sub)) {
                                if (!sid.equals(mCurNlpSid)) {
                                    mCurNlpSid = sid;
                                    mNlpResultHelper.clear();
                                    mFeifeiAnsText.setText("");
                                }

                                mNlpResultHelper.processNlpResult(sub, cntJson);
                                String curText = mNlpResultHelper.getCurText();
                                mFeifeiAnsText.append(curText);

                                if (mNlpResultHelper.isLastResult()) {
                                    int pos = mChatAdapter.add(new ChatItem(ItemType.FEIFEI,
                                                    mFeifeiAnsText.getText().toString()));
                                    mChatAdapter.notifyItemRangeInserted(pos, 1);
                                }
                            }
                        }
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                }
                break;

                case AIUIConstant.EVENT_ERROR: {
                    // 忽略识别结果为空
                    if (10024 != event.arg1) {
                        showTip("error=" + event.arg1 + ", des=" + event.info);
                    }
                }
                break;

                case AIUIConstant.EVENT_VAD: {
                    if (AIUIConstant.VAD_EOS == event.arg1) {
                        mUserIndicator.clear();
                    } else if (AIUIConstant.VAD_VOL == event.arg1) {
                        mUserIndicator.updateVol(event.arg2);
                    }
                }
                break;

                case AIUIConstant.EVENT_START_RECORD: {
//                    showTip("录音已开始");
                }
                break;

                case AIUIConstant.EVENT_STOP_RECORD: {
//                    showTip("录音已关闭");
                }
                break;

                case AIUIConstant.EVENT_STATE: {    // 状态事件
                }
                break;

                case AIUIConstant.EVENT_TTS: {  // 合成事件
                    Log.d(TAG, "EVENT_TTS, arg1=" + event.arg1);

                    if (AIUIConstant.TTS_SPEAK_BEGIN == event.arg1) {
                        mMockHandler.sendEmptyMessage(MSG_MOCK_VOL);
                    } else if (AIUIConstant.TTS_SPEAK_COMPLETED == event.arg1) {
                        mMockHandler.sendEmptyMessage(MSG_MOCK_CLEAR);
                    }
                }
                break;

                default:
                    break;
            }
        }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE) {
            for (int i = 0; i < permissions.length; i++) {
                if (Manifest.permission.WRITE_EXTERNAL_STORAGE.equals(permissions[i]) &&
                    grantResults[i] == PermissionChecker.PERMISSION_GRANTED) {
                    createAgent();
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        destroyAgent();

        if (mMockThread != null) {
            mMockThread.quit();
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

    private HandlerThread mMockThread;

    private MockHandler mMockHandler;

    private static final int MSG_MOCK_VOL = 1;

    private static final int MSG_MOCK_CLEAR = 2;

    private class MockHandler extends Handler {
        public MockHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_MOCK_VOL: {
                    removeMessages(MSG_MOCK_VOL);
                    int vol = new Random(System.currentTimeMillis()).nextInt(26);

                    runOnUiThread(() -> {
                        mFeifeiIndicator.updateVol(vol);
                    });

                    sendEmptyMessageDelayed(MSG_MOCK_VOL, 40);
                } break;

                case MSG_MOCK_CLEAR: {
                    removeMessages(MSG_MOCK_VOL);

                    runOnUiThread(() -> {
                        mFeifeiIndicator.clear();
                    });
                } break;
            }
        }
    }

    private enum ItemType {
        USER,
        FEIFEI
    }

    private class ChatItem {
        public ItemType mType;
        public String mContent = "";

        public ChatItem(ItemType type, String content) {
            mType = type;
            mContent = content;
        }
    }

    private class ChatItemVH extends RecyclerView.ViewHolder {
        private final TextView mContentText;

        public ChatItemVH(@NonNull View itemView) {
            super(itemView);

            mContentText = itemView.findViewById(R.id.txt_content);
        }

        public void bind(ChatItem item) {
            mContentText.setText(item.mContent);
        }
    }

    private final ChatAdapter mChatAdapter = new ChatAdapter();

    private class ChatAdapter extends RecyclerView.Adapter<ChatItemVH> {
        private final List<ChatItem> mItemList = new ArrayList<>();

        public ChatAdapter() {

        }

        public int add(ChatItem item) {
            mItemList.add(item);

            return mItemList.size() - 1;
        }

        public void clear() {
            mItemList.clear();
        }

        @Override
        public int getItemCount() {
            return mItemList.size();
        }

        @NonNull
        @Override
        public ChatItemVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ChatItemVH vh = null;
            switch (ItemType.values()[viewType]) {
                case USER: {
                    View view = LayoutInflater.from(parent.getContext()).inflate(
                                    R.layout.layout_chat_item_user, parent, false);
                    vh = new ChatItemVH(view);
                } break;

                case FEIFEI: {
                    View view = LayoutInflater.from(parent.getContext()).inflate(
                            R.layout.layout_chat_item_feifei, parent, false);
                    vh = new ChatItemVH(view);
                } break;
            }

            return vh;
        }

        @Override
        public void onBindViewHolder(@NonNull ChatItemVH holder, int position) {
            holder.bind(mItemList.get(position));
        }

        @Override
        public int getItemViewType(int position) {
            return mItemList.get(position).mType.ordinal();
        }
    }
}
