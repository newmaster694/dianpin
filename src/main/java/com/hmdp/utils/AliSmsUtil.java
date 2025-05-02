package com.hmdp.utils;

import com.aliyun.auth.credentials.Credential;
import com.aliyun.auth.credentials.provider.StaticCredentialProvider;
import com.aliyun.sdk.service.dysmsapi20170525.AsyncClient;
import com.aliyun.sdk.service.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.sdk.service.dysmsapi20170525.models.SendSmsResponse;
import com.google.gson.Gson;
import darabonba.core.client.ClientOverrideConfiguration;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;

@Data
@AllArgsConstructor
@Slf4j
public class AliSmsUtil {
	
	private String accessKeyId;
	private String accessKeySecret;
	private String signName;
	
	//阿里云短信验证码发送工具类
	public void sendMsg(String code, String phone) throws Exception {
		StaticCredentialProvider provider = StaticCredentialProvider.create(Credential.builder()
				.accessKeyId(accessKeyId)
				.accessKeySecret(accessKeySecret)
				.build());
		
		AsyncClient client = AsyncClient.builder()
				.credentialsProvider(provider)
				.overrideConfiguration(
						ClientOverrideConfiguration.create()
								.setEndpointOverride("dysmsapi.aliyuncs.com")
				)
				.build();
		
		SendSmsRequest sendSmsRequest = SendSmsRequest.builder()
				.phoneNumbers(phone)
				.signName(signName)
				.templateCode(code)
				.build();
		
		CompletableFuture<SendSmsResponse> response = client.sendSms(sendSmsRequest);
		
		SendSmsResponse resp = response.get();
		System.out.println(new Gson().toJson(resp));
		
		client.close();
	}
}
