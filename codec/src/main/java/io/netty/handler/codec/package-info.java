/**
 * Extensible decoder and its common implementations which deal with the
 * packet fragmentation and reassembly issue found in a stream-based transport
 * such as TCP/IP.
 *
 * 该项目实现了Netty 架构图中的 Protocol Support 。
 *
 * codec 项目，该项目是协议编解码的抽象与部分实现：JSON、Google Protocol、Base64、XML 等等。
 *
 * 另外，它提供了多个子项目，实现不同协议的编解码。例如：codec-dns、codec-haproxy、codec-http、
 * codec-http2、codec-mqtt、codec-redis、codec-memcached、codec-smtp、codec-socks、codec-stomp、codec-xml 等等。
 */
package io.netty.handler.codec;
