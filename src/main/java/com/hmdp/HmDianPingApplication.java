package com.hmdp;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@MapperScan("com.hmdp.mapper")
@SpringBootApplication
@EnableAspectJAutoProxy(exposeProxy = true) //暴露代理对象注解
@Slf4j
public class HmDianPingApplication {
	
	public static void main(String[] args) {
		SpringApplication.run(HmDianPingApplication.class, args);
		log.info("项目启动成功");
	}
	
}
