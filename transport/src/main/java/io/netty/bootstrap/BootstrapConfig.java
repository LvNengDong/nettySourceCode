package io.netty.bootstrap;

import io.netty.channel.Channel;
import io.netty.resolver.AddressResolverGroup;

import java.net.SocketAddress;

/**
 * 每个 Config 类，对应一个 Bootstrap 类
 */
public final class BootstrapConfig extends AbstractBootstrapConfig<Bootstrap, Channel> {

    BootstrapConfig(Bootstrap bootstrap) {
        super(bootstrap);
    }

    public SocketAddress remoteAddress() {
        return bootstrap.remoteAddress();
    }

    public AddressResolverGroup<?> resolver() {
        return bootstrap.resolver();
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder(super.toString());
        buf.setLength(buf.length() - 1);
        AddressResolverGroup<?> resolver = resolver();
        if (resolver != null) {
            buf.append(", resolver: ")
                    .append(resolver);
        }
        SocketAddress remoteAddress = remoteAddress();
        if (remoteAddress != null) {
            buf.append(", remoteAddress: ")
                    .append(remoteAddress);
        }
        return buf.append(')').toString();
    }
}
