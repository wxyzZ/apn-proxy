/*
 * Copyright (c) 2014 The APN-PROXY Project
 *
 * The APN-PROXY Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.xx_dev.apn.proxy;

import com.xx_dev.apn.proxy.remotechooser.ApnProxyRemote;
import com.xx_dev.apn.proxy.utils.Base64;
import com.xx_dev.apn.proxy.utils.LoggerUtil;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.util.Set;

/**
 * @author xmx
 * @version $Id: com.xx_dev.apn.proxy.ApnProxyUserAgentTunnelHandler 14-1-8 16:13 (xmx) Exp $
 */
public class ApnProxyUserAgentTunnelHandler extends ChannelInboundHandlerAdapter {

    private static final Logger logger = Logger.getLogger(ApnProxyUserAgentTunnelHandler.class);

    public static final String HANDLER_NAME = "apnproxy.useragent.tunnel";

    @Override
    public void channelRead(final ChannelHandlerContext uaChannelCtx, Object msg) throws Exception {

        if (msg instanceof HttpRequest) {
            final HttpRequest httpRequest = (HttpRequest) msg;

            //Channel uaChannel = uaChannelCtx.channel();

            // connect remote
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(uaChannelCtx.channel().eventLoop()).channel(NioSocketChannel.class)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                    .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                    .option(ChannelOption.AUTO_READ, false)
                    .handler(new ApnProxyTunnelChannelInitializer(uaChannelCtx.channel()));

            final ApnProxyRemote apnProxyRemote = uaChannelCtx.channel()
                    .attr(ApnProxyConnectionAttribute.ATTRIBUTE_KEY).get().getRemote();

            // set local address
            if (StringUtils.isNotBlank(ApnProxyLocalAddressChooser.choose(apnProxyRemote
                    .getRemoteHost()))) {
                bootstrap.localAddress(new InetSocketAddress((ApnProxyLocalAddressChooser
                        .choose(apnProxyRemote.getRemoteHost())), 0));
            }

            bootstrap.connect(apnProxyRemote.getRemoteHost(), apnProxyRemote.getRemotePort())
                    .addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(final ChannelFuture future1) throws Exception {
                            if (future1.isSuccess()) {
                                if (apnProxyRemote.isAppleyRemoteRule()) {
                                    uaChannelCtx.pipeline().remove("codec");
                                    uaChannelCtx.pipeline().remove(ApnProxyPreHandler.HANDLER_NAME);
                                    uaChannelCtx.pipeline().remove(ApnProxyUserAgentTunnelHandler.HANDLER_NAME);

                                    // add relay handler
                                    uaChannelCtx.pipeline().addLast(
                                            new ApnProxyRelayHandler("UA --> Remote", future1.channel()));

                                    future1.channel().writeAndFlush(
                                            Unpooled.copiedBuffer(
                                                    constructConnectRequestForProxy(httpRequest,
                                                            apnProxyRemote), CharsetUtil.UTF_8))
                                            .addListener(new ChannelFutureListener() {
                                                @Override
                                                public void operationComplete(ChannelFuture future2)
                                                        throws Exception {
                                                    if (!future2.channel().config()
                                                            .getOption(ChannelOption.AUTO_READ)) {
                                                        future2.channel().read();
                                                    }
                                                }
                                            });

                                } else {
                                    HttpResponse proxyConnectSuccessResponse = new DefaultFullHttpResponse(
                                            HttpVersion.HTTP_1_1, new HttpResponseStatus(200,
                                            "Connection established"));
                                    uaChannelCtx.writeAndFlush(proxyConnectSuccessResponse).addListener(
                                            new ChannelFutureListener() {
                                                @Override
                                                public void operationComplete(ChannelFuture future2)
                                                        throws Exception {
                                                    // remove handlers
                                                    uaChannelCtx.pipeline().remove("codec");
                                                    uaChannelCtx.pipeline().remove(ApnProxyPreHandler.HANDLER_NAME);
                                                    uaChannelCtx.pipeline().remove(
                                                            ApnProxyUserAgentTunnelHandler.HANDLER_NAME);

                                                    // add relay handler
                                                    uaChannelCtx.pipeline().addLast(
                                                            new ApnProxyRelayHandler("UA --> "
                                                                    + apnProxyRemote
                                                                    .getRemoteAddr(), future1
                                                                    .channel()));
                                                }

                                            });
                                }

                            } else {
                                if (uaChannelCtx.channel().isActive()) {
                                    uaChannelCtx.channel().writeAndFlush(Unpooled.EMPTY_BUFFER)
                                            .addListener(ChannelFutureListener.CLOSE);
                                }
                            }
                        }
                    });

        }
        ReferenceCountUtil.release(msg);
    }

    private String constructConnectRequestForProxy(HttpRequest httpRequest,
                                                   ApnProxyRemote apnProxyRemote) {
        String CRLF = "\r\n";
        String url = httpRequest.getUri();
        StringBuilder sb = new StringBuilder();
        sb.append(httpRequest.getMethod().name()).append(" ").append(url).append(" ")
                .append(httpRequest.getProtocolVersion().text()).append(CRLF);

        Set<String> headerNames = httpRequest.headers().names();
        for (String headerName : headerNames) {
            if (StringUtils.equalsIgnoreCase(headerName, "Proxy-Connection")) {
                continue;
            }

            if (StringUtils.equalsIgnoreCase(headerName, HttpHeaders.Names.CONNECTION)) {
                continue;
            }

            for (String headerValue : httpRequest.headers().getAll(headerName)) {
                sb.append(headerName).append(": ").append(headerValue).append(CRLF);
            }
        }

        if (StringUtils.isNotBlank(apnProxyRemote.getProxyUserName())
                && StringUtils.isNotBlank(apnProxyRemote.getProxyPassword())) {
            String proxyAuthorization = apnProxyRemote.getProxyUserName() + ":"
                    + apnProxyRemote.getProxyPassword();
            try {
                sb.append(
                        "Proxy-Authorization: Basic "
                                + Base64.encodeBase64String(proxyAuthorization.getBytes("UTF-8")))
                        .append(CRLF);
            } catch (UnsupportedEncodingException e) {
            }

        }

        sb.append(CRLF);

        return sb.toString();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        LoggerUtil.error(logger, cause.getMessage(), cause, ctx.attr(ApnProxyConnectionAttribute.ATTRIBUTE_KEY));
        ctx.close();
    }
}
