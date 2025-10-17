package com.iflytek.aiui.demo.chat.utils.iat;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by hj at 2024/6/14 11:05
 * <p>
 * 听写结果工具类。
 */
public class IatResultHelper {
    // 用map来存储pgs结果
    private final Map<Integer, String> mPgsResultMap = new HashMap<>();

    private String mLastIatResult = "";

    private boolean mIsLastResult = false;

    public void clear() {
        mPgsResultMap.clear();
        mLastIatResult = "";
        mIsLastResult = false;
    }

    public boolean isLastResult() {
        return mIsLastResult;
    }

    /**
     * 解析听写结果。
     */
    public String processIATResult(JSONObject cntJson) throws JSONException {
        JSONObject text = cntJson.optJSONObject("text");

        // 解析并拼接得到此次听写结果
        StringBuilder iatText = new StringBuilder();
        JSONArray words = text.optJSONArray("ws");
        boolean lastResult = text.optBoolean("ls");
        mIsLastResult = lastResult;

        for (int index = 0; index < words.length(); index++) {
            JSONArray charWord = words.optJSONObject(index).optJSONArray("cw");
            for (int cIndex = 0; cIndex < charWord.length(); cIndex++) {
                iatText.append(charWord.optJSONObject(cIndex).opt("w"));
            }
        }

        String voiceIAT = "";
        String pgsMode = text.optString("pgs");

        if (TextUtils.isEmpty(pgsMode)) {
            // 非PGS模式结果
            if (TextUtils.isEmpty(iatText)) {
                return mLastIatResult;
            }

            // 和上一次结果进行拼接
            if (!TextUtils.isEmpty(mLastIatResult)) {
                voiceIAT = mLastIatResult;
            }

            voiceIAT += iatText;
        } else {
            int sn = text.optInt("sn");

            // pgs结果两种模式rpl和apd模式（替换和追加模式）
            if ("rpl".equals(pgsMode)) {
                // 根据replace指定的range，清空stack中对应位置值
                JSONArray replaceRange = text.optJSONArray("rg");
                int start = replaceRange.getInt(0);
                int end = replaceRange.getInt(1);

                for (int index = start; index <= end; index++) {
                    mPgsResultMap.remove(index);
                }
            }

            mPgsResultMap.put(sn, iatText.toString());

            StringBuilder pgsResult = new StringBuilder();
            for (Map.Entry<Integer, String> entry : mPgsResultMap.entrySet()) {
                pgsResult.append(entry.getValue());
            }

            voiceIAT = pgsResult.toString();

            if (lastResult) {
                mPgsResultMap.clear();
            }
        }

        if (!TextUtils.isEmpty(voiceIAT)) {
            mLastIatResult = voiceIAT;
        }

        return voiceIAT;
    }
}
