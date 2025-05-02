package com.hmdp.config;

import com.hmdp.properties.AliyunProperties;
import com.hmdp.utils.AliSmsUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class AliyunConfig {

	@Bean
	@ConditionalOnMissingBean
	public AliSmsUtil aliSmsUtil(AliyunProperties aliyunProperties) {
		log.info("开始创建阿里云短信发送服务对象...");
		return new AliSmsUtil(
				aliyunProperties.getAccessKeyId(),
				aliyunProperties.getAccessKeySecret(),
				aliyunProperties.getSignName()
		);
	}
}
