<!-- PROJECT LOGO -->
<br />

<div align="center">
  <a href="https://github.com/newmaster694/dianpin">
    <img src="images/logo.png" alt="Logo" width="80" height="80">
  </a>

  <h3 align="center">HM-DianPing</h3>

  <p align="center">
    一个基于Redis为核心技术栈的点评平台项目
    <br />
    <a href="https://github.com/newmaster694/dianpin"><strong>Explore the docs »</strong></a>
    <br />
    <br />
    <a href="http://47.83.141.64">示例网站</a>
    &middot;
    <a href="https://github.com/newmaster694/dianpin/issues/new?labels=bug&template=bug-report---.md">提交问题</a>
  </p>
</div>




<!-- ABOUT THE PROJECT -->

## 关于项目

<div align="center">
    <img src="/images/Project%20Description.jpeg" width="600">
</div>

这是一个基于Redis为核心技术栈实现的一个类似于大众点评的点评网站,旨在帮助刚刚学完Spring Boot技术与Redis的同学熟悉Java Web的开发流程,如果你觉得这个项目有帮助到你,就帮忙点个start吧

实现的功能:
- [x] 基于Redis的分布式session方案
- [x] 空值缓存解决缓存穿透/随机TTL解决缓存雪崩/互斥锁+逻辑过期解决缓存击穿功能
- [x] Lua脚本解决:一人一单/时间合法性判断/库存判断.同时保证操作原子性
- [x] Redis Stream 流创建简单的消息队列实现异步下单,减小数据库压力
- [x] Redis GEO 实现"附近商户"功能+滚动分页
- [x] BitMap 实现用户连续签到功能
- [x] 好友关注/共同关注/Feed 流推送

### 构建框架

以下为本项目的主要构建工具

[![Java][java-shield]][java-url]<br>
[![Spring][Spring Boot-shield]][Spring Boot-url]<br>
[![Redis][Redis-shield]][Redis-url]<br>
[![MySQL][MySQL-shield]][MySQL-url]<br>

<!-- GETTING STARTED -->
## Getting Started

这一部分会告诉你如何在本地快速启动这个后端服务

### 准备工作

This is an example of how to list things you need to use the software and how to install them.

### 安装环境


<!-- USAGE EXAMPLES -->
## 使用


<!-- ROADMAP -->
## 路线图



<!-- LICENSE -->
## 使用的开源许可证

Distributed under the Unlicense License. See `LICENSE.txt` for more information.


<!-- CONTACT -->
## 联系

cryingsky.47@icloud.com

Project Link: [https://github.com/newmaster694/dianpin](https://github.com/newmaster694/dianpin)

<p align="right">(<a href="#readme-top">back to top</a>)</p>

<!-- LINKS -->
[java-shield]:https://img.shields.io/badge/java-1.8-007396?style=for-the-badge&logo=intellijidea&logoColor=white
[Spring Boot-shield]:https://img.shields.io/badge/Spring%20Boot-2.3.12-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white
[Redis-shield]:https://img.shields.io/badge/Redis-7.4.0-DC382D?style=for-the-badge&logo=redis&logoColor=white
[MySQL-shield]:https://img.shields.io/badge/MySQL-8.1.0-4479A1?style=flat&logo=mysql&logoColor=white

[java-url]:https://www.oracle.com/cn/java/technologies/downloads/
[Spring Boot-url]:https://spring.io/projects/spring-boot
[Redis-url]:https://redis.io/
[MySQL-url]:https://www.mysql.com/
