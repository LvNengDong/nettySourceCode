package io.netty.bootstrap;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ReflectiveChannelFactory;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.SocketUtils;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.logging.InternalLogger;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ServerBootstrap 和 Bootstrap 都继承了这个类
 * <p>
 * AbstractBootstrap 声明了 B 、C 两个泛型：
 * B ：继承 AbstractBootstrap 类，用于表示自身的类型。
 * C ：继承 Channel 类，表示表示创建的 Channel 类型。
 */
public abstract class AbstractBootstrap<B extends AbstractBootstrap<B, C>, C extends Channel> implements Cloneable {
    @SuppressWarnings("unchecked")
    private static final Map.Entry<ChannelOption<?>, Object>[] EMPTY_OPTION_ARRAY = new Map.Entry[0];
    @SuppressWarnings("unchecked")
    private static final Map.Entry<AttributeKey<?>, Object>[] EMPTY_ATTRIBUTE_ARRAY = new Map.Entry[0];

    /* EventLoopGroup 对象 */
    volatile EventLoopGroup group;
    /* Channel 工厂，用于创建 Channel 对象 */
    @SuppressWarnings("deprecation")
    private volatile ChannelFactory<? extends C> channelFactory;

    /* 本地地址 */
    private volatile SocketAddress localAddress;

    /* 可选项集合 */
    private final Map<ChannelOption<?>, Object> options = new LinkedHashMap<ChannelOption<?>, Object>();

    /* 属性集合 */
    private final Map<AttributeKey<?>, Object> attrs = new ConcurrentHashMap<AttributeKey<?>, Object>();

    /**
     * 处理器
     *     对于服务端而言，是 NioServerSocketChannel 的处理器
     *     对于客户端而言，是 SocketChannel 的处理器
     * */
    private volatile ChannelHandler handler;

    private volatile ClassLoader extensionsClassLoader;

    AbstractBootstrap() {
        // Disallow extending from a different package.
    }

    AbstractBootstrap(AbstractBootstrap<B, C> bootstrap) {
        group = bootstrap.group;
        channelFactory = bootstrap.channelFactory;
        handler = bootstrap.handler;
        localAddress = bootstrap.localAddress;
        synchronized (bootstrap.options) {
            options.putAll(bootstrap.options);
        }
        attrs.putAll(bootstrap.attrs);
        extensionsClassLoader = bootstrap.extensionsClassLoader;
    }

    /**
     * 设置入参中的 EventLoopGroup 到 group 中
     */
    public B group(EventLoopGroup group) {
        ObjectUtil.checkNotNull(group, "group");
        if (this.group != null) { // group 只能被设置一次，不允许重复设置
            throw new IllegalStateException("group set already");
        }
        this.group = group;
        return self(); // 最终调用 #self() 方法，返回自己。实际上，AbstractBootstrap 整个方法的调用，基本都是“链式调用”。
    }

    /**
     * 返回自己，
     * 返回的 AbstractBootstrap 的子类实现类类型，这里就使用到了 AbstractBootstrap 声明的 B 泛型。
     */
    @SuppressWarnings("unchecked")
    private B self() {
        return (B) this;
    }

    /**

     */
    /**
     * 设置要被实例化的 Channel 的类：从入参中获取一个 Channel 实现类的 Class 对象，为该 Class 绑定一个 Factory，
     * 并将 Factory 与 对应的 Bootstrap 绑定，在真正需要的时候，由 Bootstrap 调用 Factory，Factory 调用 Class
     * 对象的构造方法，通过反射创建对应的 Class 类的实例对象。
     *      对于服务端 ServerBootstrap 而言，推荐的 Channel 为 NioServerSocketChannel
     *      对于客户端 Bootstrap 而言，推荐的 Channel 为 NioSocketChannel
     *
     * @param channelClass 入参是 Channel 实现类的 Class 对象
     * @return 返回值是 Bootstrap 对象，这个方法的作用是创建一个 Factory，将 Factory 绑定到 Bootstrap 对象的 channelFactory 属性中并返回 Bootstrap 对象
     */
    public B channel(Class<? extends C> channelClass) {
        // 创建一个 ChannelFactory
        return channelFactory(
                // 虽然传入的 channelClass 参数，但是会使用 io.netty.channel.ReflectiveChannelFactory 进行封装
                new ReflectiveChannelFactory<C>(ObjectUtil.checkNotNull(channelClass, "channelClass")));
    }

    @Deprecated
    public B channelFactory(ChannelFactory<? extends C> channelFactory) {
        ObjectUtil.checkNotNull(channelFactory, "channelFactory");
        if (this.channelFactory != null) {
            throw new IllegalStateException("channelFactory set already");
        }
        // 设置 channelFactory 属性
        this.channelFactory = channelFactory;
        return self();
    }

    @SuppressWarnings({"unchecked", "deprecation"})
    public B channelFactory(io.netty.channel.ChannelFactory<? extends C> channelFactory) {
        return channelFactory((ChannelFactory<C>) channelFactory);
    }

    /**
     * 设置 Channel 的本地地址。有四个重载的方法
     *
     * 一般情况下，不会调用该方法进行配置，而是调用 #bind(...) 方法
     */
    public B localAddress(SocketAddress localAddress) {
        this.localAddress = localAddress;
        return self();
    }
    public B localAddress(int inetPort) {
        return localAddress(new InetSocketAddress(inetPort));
    }
    public B localAddress(String inetHost, int inetPort) {
        return localAddress(SocketUtils.socketAddress(inetHost, inetPort));
    }
    public B localAddress(InetAddress inetHost, int inetPort) {
        return localAddress(new InetSocketAddress(inetHost, inetPort));
    }


    /**
     * 设置 Channel 的可选项
     * value 为空则移除 option
     * value 非空则新增或修改 option
     */
    public <T> B option(ChannelOption<T> option, T value) {
        ObjectUtil.checkNotNull(option, "option");
        synchronized (options) {
            if (value == null) { // 空，意味着移除
                options.remove(option);
            } else { // 非空，进行修改
                options.put(option, value);
            }
        }
        return self();
    }

    /**
     * 设置 Channel 的属性
     * value 为空则移除 attr
     * value 非空则新增或修改 attr
     *
     * 怎么理解 attrs 属性呢？我们可以理解成 java.nio.channels.SelectionKey 的 attachment 属性，并且类型为 Map 。
     */
    public <T> B attr(AttributeKey<T> key, T value) {
        ObjectUtil.checkNotNull(key, "key");
        if (value == null) {
            attrs.remove(key);
        } else {
            attrs.put(key, value);
        }
        return self();
    }

    /**
     * Load {@link ChannelInitializerExtension}s using the given class loader.
     * <p>
     * By default, the extensions will be loaded by the same class loader that loaded this bootstrap class.
     *
     * @param classLoader The class loader to use for loading {@link ChannelInitializerExtension}s.
     * @return This bootstrap.
     */
    public B extensionsClassLoader(ClassLoader classLoader) {
        extensionsClassLoader = classLoader;
        return self();
    }

    /**
     * 校验配置是否正确
     *  在 #bind(...) 方法中，绑定本地地址时，会调用该方法进行校验。
     */
    public B validate() {
        if (group == null) {
            throw new IllegalStateException("group not set");
        }
        if (channelFactory == null) {
            throw new IllegalStateException("channel or channelFactory not set");
        }
        return self();
    }

    /**
     * 抽象方法，克隆一个 AbstractBootstrap 对象
     */
    @Override
    @SuppressWarnings("CloneDoesntDeclareCloneNotSupportedException")
    public abstract B clone();

    /**
     * Create a new {@link Channel} and register it with an {@link EventLoop}.
     */
    public ChannelFuture register() {
        validate();
        return initAndRegister();
    }

    /**
     * 创建一个 Channel 对象并绑定网络地址（IP+端口）
     * #bind(...) 方法，也可以启动 UDP 的一端，考虑到这个系列主要分享 Netty 在 NIO 相关的源码解析，所以如下所有的分享，都不考虑 UDP 的情况。
     */
    public ChannelFuture bind() {
        validate(); // 校验服务启动需要的必要参数
        SocketAddress localAddress = this.localAddress;
        if (localAddress == null) {
            throw new IllegalStateException("localAddress not set");
        }
        return doBind(localAddress);
    }

    /**
     * Create a new {@link Channel} and bind it.
     * 创建服务端 Channel 的入口
     *
     * 创建一个 Channel 对象并绑定网络地址（IP+端口）
     */
    public ChannelFuture bind(SocketAddress localAddress) {
        validate();
        return doBind(ObjectUtil.checkNotNull(localAddress, "localAddress"));
    }
    public ChannelFuture bind(int inetPort) {
        return bind(new InetSocketAddress(inetPort));
    }
    public ChannelFuture bind(String inetHost, int inetPort) {
        return bind(SocketUtils.socketAddress(inetHost, inetPort));
    }
    public ChannelFuture bind(InetAddress inetHost, int inetPort) {
        return bind(new InetSocketAddress(inetHost, inetPort));
    }

    /**
     * 创建一个 Channel 对象并绑定网络地址（IP+端口）
     * @param localAddress Channel 要绑定的地址
     * @return 该方法返回的是 ChannelFuture 对象，也就是异步的绑定端口，启动服务端。
     * 如果需要同步，则需要调用 ChannelFuture#sync() 方法
     */
    private ChannelFuture doBind(final SocketAddress localAddress) {
        // 初始化并注册一个 Channel 对象，因为注册是异步的过程，所以返回一个 ChannelFuture 对象
        final ChannelFuture regFuture = initAndRegister();
        final Channel channel = regFuture.channel();
        if (regFuture.cause() != null) { // 若发生异常，直接返回
            return regFuture;
        }

        // 绑定 Channel 的端口，并注册 Channel 到 SelectionKey 中
        /*
        因为注册是异步的过程，有可能已完成，有可能未完成。所以实现代码分成了两部分分别处理已完成和未完成的情况。
        1、如果异步注册对应的 ChanelFuture 已完成，调用 #doBind0 方法，绑定 Channel 的端口，并注册 Channel 到 SelectionKey 中。
        2、如果异步注册对应的 ChanelFuture 未完成，则调用 addListener 方法，添加监听器，在注册完成后，进行回调执行 #doBind0 方的逻辑。
        */
        if (regFuture.isDone()) {
            // At this point we know that the registration was complete and successful.
            ChannelPromise promise = channel.newPromise();
            doBind0(regFuture, channel, localAddress, promise); // 绑定
            return promise;
        } else {
            // Registration future is almost always fulfilled already, but just in case it's not.
            final PendingRegistrationPromise promise = new PendingRegistrationPromise(channel);
            regFuture.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    Throwable cause = future.cause();
                    if (cause != null) {
                        // Registration on the EventLoop failed so fail the ChannelPromise directly to not cause an
                        // IllegalStateException once we try to access the EventLoop of the Channel.
                        promise.setFailure(cause);
                    } else {
                        // Registration was successful, so set the correct executor to use.
                        // See https://github.com/netty/netty/issues/2586
                        promise.registered();

                        doBind0(regFuture, channel, localAddress, promise); // 绑定
                    }
                }
            });
            return promise;
        }
    }

    final ChannelFuture initAndRegister() {
        Channel channel = null;
        try {
            // 1、创建 Channel 对象
            channel = channelFactory.newChannel();
            // 2、初始化 Channel 配置
            init(channel);
        } catch (Throwable t) {
            /* 已创建 Channel 对象，所以这个分支的异常一定是初始化 Channel 配置时抛出的异常 */
            if (channel != null) {
                // 返回带异常的 DefaultChannelPromise 对象。因为初始化 Channel 对象失败，所以需要调用 #closeForcibly() 方法，强制关闭 Channel 。
                channel.unsafe().closeForcibly(); // 强制关闭 Channel
                return new DefaultChannelPromise(channel, GlobalEventExecutor.INSTANCE).setFailure(t);
            }
            /* 尚未创建 Channel 对象，所以这个分支的异常一定是创建 Channel 对象时抛出的异常*/
            // 返回带异常的 DefaultChannelPromise 对象。因为创建 Channel 对象失败，所以需要创建一个 FailedChannel 对象，设置到 DefaultChannelPromise 中才可以返回。
            return new DefaultChannelPromise(new FailedChannel(), GlobalEventExecutor.INSTANCE).setFailure(t);
        }

        // 3、注册 Channel 到 EventLoopGroup 中【register(channel)】
        /* 首先获得 EventLoopGroup 对象，后调用 EventLoopGroup#register(Channel) 方法，注册 Channel 到 EventLoopGroup 中。
        实际在方法内部，EventLoopGroup 会分配一个 EventLoop 对象，将 Channel 注册到其上。*/
        ChannelFuture regFuture = config().group().register(channel);
        if (regFuture.cause() != null) {
            if (channel.isRegistered()) { // 若发生异常，并且 Channel 已经注册成功，则调用 #close() 方法，正常关闭 Channel
                channel.close();
            } else { // 若发生异常，并且 Channel 并未注册成功，则调用 #closeForcibly() 方法，强制关闭 Channel 。
                channel.unsafe().closeForcibly(); // 强制关闭 Channel
            }
        }
        return regFuture;
    }

    /**
     * 初始化 Channel 配置。
     * 它是个抽象方法，由子类 ServerBootstrap 或 Bootstrap 自己实现。
     * @param channel Netty Channel 对象
     */
    abstract void init(Channel channel) throws Exception;

    Collection<ChannelInitializerExtension> getInitializerExtensions() {
        ClassLoader loader = extensionsClassLoader;
        if (loader == null) {
            loader = getClass().getClassLoader();
        }
        return ChannelInitializerExtensions.getExtensions().extensions(loader);
    }

    private static void doBind0(
            final ChannelFuture regFuture, final Channel channel,
            final SocketAddress localAddress, final ChannelPromise promise) {

        // This method is invoked before channelRegistered() is triggered.  Give user handlers a chance to set up
        // the pipeline in its channelRegistered() implementation.
        channel.eventLoop().execute(new Runnable() {
            @Override
            public void run() {
                if (regFuture.isSuccess()) { // 注册成功，绑定端口
                    channel.bind(localAddress, promise).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
                } else { // 注册失败，回调通知 promise 异常
                    promise.setFailure(regFuture.cause());
                }
            }
        });
    }

    /**
     * 设置 Channel 的处理器
     */
    public B handler(ChannelHandler handler) {
        this.handler = ObjectUtil.checkNotNull(handler, "handler");
        return self();
    }

    /**
     * Returns the configured {@link EventLoopGroup} or {@code null} if non is configured yet.
     *
     * @deprecated Use {@link #config()} instead.
     */
    @Deprecated
    public final EventLoopGroup group() {
        return group;
    }

    /**
     * 抽象方法
     * 返回当前 AbstractBootstrap 的配置对象
     */
    public abstract AbstractBootstrapConfig<B, C> config();

    final Map.Entry<ChannelOption<?>, Object>[] newOptionsArray() {
        return newOptionsArray(options);
    }

    static Map.Entry<ChannelOption<?>, Object>[] newOptionsArray(Map<ChannelOption<?>, Object> options) {
        synchronized (options) {
            return new LinkedHashMap<ChannelOption<?>, Object>(options).entrySet().toArray(EMPTY_OPTION_ARRAY);
        }
    }

    final Map.Entry<AttributeKey<?>, Object>[] newAttributesArray() {
        return newAttributesArray(attrs0());
    }

    static Map.Entry<AttributeKey<?>, Object>[] newAttributesArray(Map<AttributeKey<?>, Object> attributes) {
        return attributes.entrySet().toArray(EMPTY_ATTRIBUTE_ARRAY);
    }

    final Map<ChannelOption<?>, Object> options0() {
        return options;
    }

    final Map<AttributeKey<?>, Object> attrs0() {
        return attrs;
    }

    final SocketAddress localAddress() {
        return localAddress;
    }

    @SuppressWarnings("deprecation")
    final ChannelFactory<? extends C> channelFactory() {
        return channelFactory;
    }

    final ChannelHandler handler() {
        return handler;
    }

    final Map<ChannelOption<?>, Object> options() {
        synchronized (options) {
            return copiedMap(options);
        }
    }

    final Map<AttributeKey<?>, Object> attrs() {
        return copiedMap(attrs);
    }

    static <K, V> Map<K, V> copiedMap(Map<K, V> map) {
        if (map.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(new HashMap<K, V>(map));
    }

    static void setAttributes(Channel channel, Map.Entry<AttributeKey<?>, Object>[] attrs) {
        for (Map.Entry<AttributeKey<?>, Object> e : attrs) {
            @SuppressWarnings("unchecked")
            AttributeKey<Object> key = (AttributeKey<Object>) e.getKey();
            channel.attr(key).set(e.getValue());
        }
    }

    /**
     * 静态方法，设置传入的 Channel 的多个可选项
     */
    static void setChannelOptions(Channel channel, Map.Entry<ChannelOption<?>, Object>[] options, InternalLogger logger) {
        for (Map.Entry<ChannelOption<?>, Object> e : options) {
            setChannelOption(channel, e.getKey(), e.getValue(), logger);
        }
    }

    /**
     * 设置传入的 Channel 的一个可选项
     *
     * 不同于「option」 方法，option 方法是设置要创建的 Channel 的可选项。
     * 而 #setChannelOption(...) 方法，它是设置已经创建的 Channel 的可选项。
     * */
    @SuppressWarnings("unchecked")
    private static void setChannelOption(Channel channel, ChannelOption<?> option, Object value, InternalLogger logger) {
        try {
            if (!channel.config().setOption((ChannelOption<Object>) option, value)) {
                logger.warn("Unknown channel option '{}' for channel '{}'", option, channel);
            }
        } catch (Throwable t) {
            logger.warn(
                    "Failed to set channel option '{}' with value '{}' for channel '{}'", option, value, channel, t);
        }
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder()
                .append(StringUtil.simpleClassName(this))
                .append('(').append(config()).append(')');
        return buf.toString();
    }

    static final class PendingRegistrationPromise extends DefaultChannelPromise {

        // Is set to the correct EventExecutor once the registration was successful. Otherwise it will
        // stay null and so the GlobalEventExecutor.INSTANCE will be used for notifications.
        private volatile boolean registered;

        PendingRegistrationPromise(Channel channel) {
            super(channel);
        }

        void registered() {
            registered = true;
        }

        @Override
        protected EventExecutor executor() {
            if (registered) {
                // If the registration was a success executor is set.
                //
                // See https://github.com/netty/netty/issues/2586
                return super.executor();
            }
            // The registration failed so we can only use the GlobalEventExecutor as last resort to notify.
            return GlobalEventExecutor.INSTANCE;
        }
    }
}
