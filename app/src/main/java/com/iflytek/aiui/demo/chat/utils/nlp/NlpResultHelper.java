package com.iflytek.aiui.demo.chat.utils.nlp;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by hj at 2025/4/21 17:40
 * <p>
 * 流式语义结果帮助类。
 */
public class NlpResultHelper {
    private static final int STATUS_BEGIN = 0;
    private static final int STATUS_CONTINUE = 1;
    private static final int STATUS_END = 2;
    private static final int STATUS_ALLONE = 3;

    public static final String SUB_NLP = "nlp";
    public static final String SUB_CBM_SEMANTIC = "cbm_semantic";

    public static final String SERVICE_TAKE_PHOTO = "take_photo";
    public static final String INTENT_TAKE_PHOTO = "take_photo";

    private boolean mIsLastResult;

    private String mCurSub = "";
    private String mCurText = "";
    private int mCurIndex = 0;
    private int mCurStatus = 0;

    private int mRc = 0;
    private String mService = "";
    private String mIntent = "";

    public void clear() {
        mCurText = "";
        mCurIndex = 0;
        mCurStatus = 0;
        mIsLastResult = false;

        mRc = 0;
        mService = "";
        mIntent = "";
    }

    public boolean isLastResult() {
        return mIsLastResult;
    }

    /**
     * 处理语义结果。
     *
     * @param cntJson
     * @return
     */
    public boolean processNlpResult(String sub, JSONObject cntJson) {
        mCurSub = sub;

        clear();

        try {
            JSONObject resultJson = cntJson.getJSONObject(sub);
            if (SUB_NLP.equals(sub)) {
                mCurText = resultJson.getString("text");
                mCurIndex = resultJson.getInt("seq");
                mCurStatus = resultJson.getInt("status");

                if (mCurStatus == STATUS_END || mCurStatus == STATUS_ALLONE) {
                    mIsLastResult = true;
                }
            } else if (SUB_CBM_SEMANTIC.equals(sub)) {
                JSONObject textJson = new JSONObject(resultJson.getString("text"));
                mRc = textJson.getInt("rc");
                if (mRc == 0) {
                    String service = textJson.getString("service");
                    if (service.contains(SERVICE_TAKE_PHOTO)) {
                        mService = SERVICE_TAKE_PHOTO;
                    }

                    JSONObject semanticJson = textJson.getJSONArray("semantic").getJSONObject(0);
                    mIntent = semanticJson.getString("intent");
                }
            } else {
                return false;
            }
        } catch (JSONException e) {
            e.printStackTrace();

            return false;
        }

        return true;
    }

    public String getCurSub() {
        return mCurSub;
    }

    public String getCurText() {
        return mCurText;
    }

    public int getCurIndex() {
        return mCurIndex;
    }

    public int getCurStatus() {
        return mCurStatus;
    }

    public int getRc() {
        return mRc;
    }

    public String getService() {
        return mService;
    }

    public String getIntent() {
        return mIntent;
    }
}
