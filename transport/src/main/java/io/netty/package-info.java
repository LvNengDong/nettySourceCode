/**
 * 该项是核心项目，实现了 Netty 架构图中 Transport Services、Universal Communication API 和 Extensible Event Model 等多部分内容。
 *
 * transport 项目，该项目是网络传输通道的抽象和实现。它定义通信的统一通信 API ，统一了 JDK 的 OIO、NIO ( 不包括 AIO )等多种编程接口。
 *
 * 另外，它提供了多个子项目，实现不同的传输类型。例如：transport-native-epoll、transport-native-kqueue、transport-rxtx、
 * transport-udt 和 transport-sctp 等等。
 */
package io.netty;