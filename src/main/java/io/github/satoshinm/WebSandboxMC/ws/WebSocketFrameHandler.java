/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
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
package io.github.satoshinm.WebSandboxMC.ws;

import java.util.Locale;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;

/**
 * Echoes uppercase content of text frames.
 */
public class WebSocketFrameHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    /* TODO: send initial server messages on client connect here, example:

U,1,0,0,0,0,0
E,1491627331.01,600
T,Welcome to Craft!
T,Type "/help" for a list of commands.
N,1,guest1

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        System.out.println("channel now active");
        ctx.channel().writeAndFlush(new BinaryWebSocketFrame(Unpooled.copiedBuffer("T,Welcome to WebSandboxMC\n".getBytes())));
    }
    */

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {
        // ping and pong frames already handled

        if (frame instanceof TextWebSocketFrame) {
            // TODO: remove text frames, not used
            // Send the uppercase string back.
            String request = ((TextWebSocketFrame) frame).text();
            System.out.println("channel " + ctx.channel() + " received request " + request);
            ctx.channel().writeAndFlush(new TextWebSocketFrame(request.toUpperCase(Locale.US)));
        } else if (frame instanceof BinaryWebSocketFrame) {
            ByteBuf content = ((BinaryWebSocketFrame) frame).content();

            byte[] bytes = new byte[content.capacity()];
            content.getBytes(0, bytes);

            String string = new String(bytes);
            System.out.println("received "+content.capacity()+" bytes: "+string);

            // TODO: handle client messages

            if (string.startsWith("V,")) { // TODO: move to channel active, but it isn't sent there? want initial client connect
                String response = "T,Welcome to WebSandboxMC\n";
                // TODO: send blocks
                ctx.channel().writeAndFlush(new BinaryWebSocketFrame(Unpooled.copiedBuffer(response.getBytes())));
            }
        } else {
            String message = "unsupported frame type: " + frame.getClass().getName();
            throw new UnsupportedOperationException(message);
        }
    }
}
