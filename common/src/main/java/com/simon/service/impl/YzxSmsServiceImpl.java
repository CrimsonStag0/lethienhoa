package com.simon.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.simon.common.domain.ResultCode;
import com.simon.common.exception.BusinessException;
import com.simon.common.utils.SmsUtil;
import com.simon.service.SmsService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 云之讯短信服务
 *
 * @author simon
 * @date 2018-12-03
 **/

@Slf4j
@Service(value = "yzxSmsServiceImpl")
@Transactional(rollbackFor = {Exception.class})
public class YzxSmsServiceImpl implements SmsService {

    private static final long EXPIRE_SECONDS = 300 * 1000;

    @Value("${sms.clientid}")
    private String clientid;

    @Value("${sms.password}")
    private String password;

    @Value("${sms.identity-code-msg-template}")
    private String identityCodeMsgTemplate;

    @Autowired
    private org.springframework.cache.CacheManager cacheManager;

    @Override
    public boolean sendIdentifyCode(String nationCode, String mobile) {
        int code = RandomUtils.nextInt(100000, 999999);
        String content = String.format(identityCodeMsgTemplate, code);
        String result = SmsUtil.getInstance().sendSMS(clientid, password, mobile, content, "4");
        boolean ret = false;
        String responseCode = null;
        if (result != null) {
            JSONObject obj = JSONObject.parseObject(result);
            if (obj != null) {
                JSONArray jsonArray = obj.getJSONArray("data");
                if (jsonArray != null && !jsonArray.isEmpty()) {
                    JSONObject index = jsonArray.getJSONObject(0);
                    if (index != null) {
                        responseCode = index.getString("code");
                        if ("0".equals(responseCode)) {
                            ret = true;
                            //写入缓存
                            Cache cache = cacheManager.getCache("smsCache");
                            cache.put(mobile, code);
                        }
                    }
                }
            }
        }
        return ret;
    }

    @Override
    public boolean checkCode(String mobile, String code) {
        Cache cache = cacheManager.getCache("smsCache");
        Cache.ValueWrapper ele = cache.get(mobile);

        if (null == ele) {
            throw new BusinessException(ResultCode.ERROR_VERI_CODE);
        }

        String output = ele.get().toString();

        boolean result = false;

        if (StringUtils.isEmpty(output)) {
            throw new BusinessException(ResultCode.ERROR_VERI_CODE);
        }

        if (StringUtils.isNotEmpty(code) && StringUtils.isNotEmpty(output)) {
            if (code.equals(output)) {
                result = true;
                //cache.evict(mobile);//删除
            }
        }

        //删除缓存
        cache.evict(mobile);

        return result;
    }
}
