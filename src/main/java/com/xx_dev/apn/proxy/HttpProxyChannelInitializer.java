package com.xx_dev.apn.proxy;

import com.xx_dev.apn.proxy.ApnProxyRemoteChooser.ApnProxyRemote;
import com.xx_dev.apn.proxy.ApnProxyXmlConfig.ApnProxyListenType;
import com.xx_dev.apn.proxy.HttpProxyHandler.RemoteChannelInactiveCallback;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;

public class HttpProxyChannelInitializer extends ChannelInitializer<SocketChannel> {

    private ApnProxyRemote apnProxyRemote;

    private Channel uaChannel;
    private String remoteAddr;
    private RemoteChannelInactiveCallback remoteChannelInactiveCallback;

    public HttpProxyChannelInitializer(ApnProxyRemote apnProxyRemote, Channel uaChannel,
                                       String remtoeAddr,
                                       RemoteChannelInactiveCallback remoteChannelInactiveCallback) {
        this.apnProxyRemote = apnProxyRemote;
        this.uaChannel = uaChannel;
        this.remoteAddr = remtoeAddr;
        this.remoteChannelInactiveCallback = remoteChannelInactiveCallback;
    }

    @Override
    public void initChannel(SocketChannel channel) throws Exception {

        ChannelPipeline pipeline = channel.pipeline();

        if (apnProxyRemote.getRemoteListenType() == ApnProxyListenType.TRIPLE_DES) {
            // SSLEngine engine = ApnProxySSLContextFactory.getSSLContext().createSSLEngine();
            // engine.setUseClientMode(true);
            //
            // pipeline.addLast("ssl", new SslHandler(engine));

            // pipeline.addLast("encrypt", new ApnProxySimpleEncryptHandler());
            pipeline.addLast(ApnProxyTripleDesHandler.HANDLER_NAME, new ApnProxyTripleDesHandler(
                    apnProxyRemote.getRemoteTripleDesKey()));

            //            pipeline.addLast(ApnProxySimpleEncryptHandler.HANDLER_NAME,
            //                new ApnProxySimpleEncryptHandler());
        }

        channel.pipeline().addLast("codec", new HttpClientCodec());
        // pipeline.addLast("decoder", new HttpResponseDecoder());
        // channel.pipeline().addLast("decompressor", new HttpContentDecompressor());
        // channel.pipeline().addLast("aggregator", new HttpObjectAggregator(65536));
        pipeline.addLast(HttpProxyHandler.HANDLER_NAME, new HttpProxyHandler(uaChannel, remoteAddr,
                remoteChannelInactiveCallback));
    }
}
