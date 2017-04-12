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

import io.github.satoshinm.WebSandboxMC.bukkit.BlockListener;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.util.concurrent.ImmediateEventExecutor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A HTTP server which serves Web Socket requests at:
 *
 * http://localhost:8080/websocket
 *
 * Open your browser at <a href="http://localhost:8080/">http://localhost:8080/</a>, then the demo page will be loaded
 * and a Web Socket connection will be made automatically.
 *
 * This server illustrates support for the different web socket specification versions and will work with:
 *
 * <ul>
 * <li>Safari 5+ (draft-ietf-hybi-thewebsocketprotocol-00)
 * <li>Chrome 6-13 (draft-ietf-hybi-thewebsocketprotocol-00)
 * <li>Chrome 14+ (draft-ietf-hybi-thewebsocketprotocol-10)
 * <li>Chrome 16+ (RFC 6455 aka draft-ietf-hybi-thewebsocketprotocol-17)
 * <li>Firefox 7+ (draft-ietf-hybi-thewebsocketprotocol-10)
 * <li>Firefox 11+ (RFC 6455 aka draft-ietf-hybi-thewebsocketprotocol-17)
 * </ul>
 */
public final class WebSocketServerThread extends Thread {

    private int PORT;
    private boolean SSL;
    private BlockListener blockListener;

    private ChannelGroup allUsersGroup;
    private int lastPlayerID;
    private Map<ChannelId, String> channelId2name;
    private Map<String, ChannelId> name2channelId;

    private String ourExternalAddress;
    private int ourExternalPort;

    public WebSocketServerThread(int port, BlockListener blockListener, String ourExternalAddress, int ourExternalPort) {
        this.PORT = port;
        this.SSL = false; // TODO: support ssl?

        this.blockListener = blockListener;

        this.allUsersGroup = new DefaultChannelGroup(ImmediateEventExecutor.INSTANCE);
        this.lastPlayerID = 0;
        this.channelId2name = new HashMap<ChannelId, String>();
        this.name2channelId = new HashMap<String, ChannelId>();

        this.ourExternalAddress = ourExternalAddress;
        this.ourExternalPort = ourExternalPort;
    }

    @Override
    public void run() {
        try {
            // Configure SSL.
            final SslContext sslCtx;
            if (SSL) {
                SelfSignedCertificate ssc = new SelfSignedCertificate();
                sslCtx = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey()).build();
            } else {
                sslCtx = null;
            }

            EventLoopGroup bossGroup = new NioEventLoopGroup(1);
            EventLoopGroup workerGroup = new NioEventLoopGroup();
            try {
                ServerBootstrap b = new ServerBootstrap();
                b.group(bossGroup, workerGroup)
                        .channel(NioServerSocketChannel.class)
                        .handler(new LoggingHandler(LogLevel.INFO))
                        .childHandler(new WebSocketServerInitializer(sslCtx, this, ourExternalAddress, ourExternalPort));

                Channel ch = b.bind(PORT).sync().channel();

                System.out.println("Open your web browser and navigate to " +
                        (SSL ? "https" : "http") + "://127.0.0.1:" + PORT + "/index.html");

                ch.closeFuture().sync();
            } finally {
                bossGroup.shutdownGracefully();
                workerGroup.shutdownGracefully();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }



    private void sendLine(Channel channel, String message) {
        channel.writeAndFlush(new BinaryWebSocketFrame(Unpooled.copiedBuffer((message + "\n").getBytes())));
    }

    private void broadcastLine(String message) {
        allUsersGroup.writeAndFlush(new BinaryWebSocketFrame(Unpooled.copiedBuffer((message + "\n").getBytes())));
    }

    // Handle a command from the client
    public void handleNewClient(ChannelHandlerContext ctx) {
        Channel channel = ctx.channel();
        int theirID = ++this.lastPlayerID;
        String theirName = "webguest" + theirID;
        allUsersGroup.add(channel);
        this.channelId2name.put(channel.id(), theirName);
        this.name2channelId.put(theirName, channel.id());

    /* Send initial server messages on client connect here, example from Python server for comparison:

U,1,0,0,0,0,0
E,1491627331.01,600
T,Welcome to Craft!
T,Type "/help" for a list of commands.
N,1,guest1
*/

        sendLine(channel,"T,Welcome to WebSandboxMC, "+theirName+"!");

        List<World> worlds = Bukkit.getServer().getWorlds();
        sendLine(channel, "T,Worlds loaded: " + worlds.size());
        sendLine(channel, "B,0,0,0,30,0,1"); // floating grass block at (0,30,0) in chunk (0,0)
        sendLine(channel, "K,0,0,0"); // update chunk key (0,0) to 0
        sendLine(channel, "R,0,0"); // refresh chunk (0,0)

        // TODO: configurable world
        World world = worlds.get(0);

        // TODO: refactor into?
        int radius = blockListener.radius;
        int x_center = blockListener.x_center;
        int y_center = blockListener.y_center;
        int z_center = blockListener.z_center;
        int y_offset = blockListener.y_offset;
        for (int i = -radius; i < radius; ++i) {
            for (int j = -radius; j < radius; ++j) {
                for (int k = -radius; k < radius; ++k) {
                    Block block = world.getBlockAt(i + x_center, j + y_center, k + z_center);
                    int type = blockListener.toWebBlockType(block.getType());

                    sendLine(channel, "B,0,0," + (i + radius) + "," + (j + radius + y_offset) + "," + (k + radius) + "," + type);
                }
            }
        }
        sendLine(channel,"K,0,0,1");
        sendLine(channel, "R,0,0");
        sendLine(channel, "T,Blocks sent");

        // Move player on top of the new blocks
        int x_start = radius;
        int y_start = world.getHighestBlockYAt(x_center, y_center) + 1 - radius - y_offset;
        int z_start = radius;
        int rotation_x = 0;
        int rotation_y = 0;
        sendLine(channel, "U,1," + x_start + "," + y_start + "," + z_start + "," + rotation_x + "," + rotation_y );
        broadcastLine("T," + theirName + " has joined.");
    }
    // TODO: cleanup clients when they disconnect

    public void handle(String string, ChannelHandlerContext ctx) {
        if (string.startsWith("B,")) {
            System.out.println("client block update: "+string);
            String[] array = string.trim().split(",");
            if (array.length != 5) {
                throw new RuntimeException("malformed block update B, command from client: "+string);
            }
            int x = Integer.parseInt(array[1]);
            int y = Integer.parseInt(array[2]);
            int z = Integer.parseInt(array[3]);
            int type = Integer.parseInt(array[4]);

            Material material = blockListener.toBukkitBlockType(type);
            int radius = blockListener.radius;
            int x_center = blockListener.x_center;
            int y_center = blockListener.y_center;
            int y_offset = blockListener.y_offset;
            int z_center = blockListener.z_center;
            x += -radius + x_center;
            y += -radius + y_center - y_offset;
            z += -radius + z_center;
            Block block = Bukkit.getServer().getWorlds().get(0).getBlockAt(x, y, z);
            if (block != null) {
                System.out.println("setting block ("+x+","+y+","+z+",) to "+material);
                block.setType(material);
            } else {
                System.out.println("no such block at "+x+","+y+","+z);
            }
        } else if (string.startsWith("T,")) {
            String chat = string.substring(2).trim();
            String theirName = this.channelId2name.get(ctx.channel().id());
            String formattedChat = "<" + theirName + "> " + chat;
            broadcastLine("T," + formattedChat);
            Bukkit.getServer().broadcastMessage(formattedChat); // TODO: only to permission name?

            // TODO: support some server /commands?
        }
        // TODO: handle more client messages
    }

    public void notifyBlockUpdate(int x, int y, int z, Material material) {
        System.out.println("bukkit block ("+x+","+y+","+z+") was set to "+material);

        // Send to all web clients within range, if within range, "B," command
        int type = blockListener.toWebBlockType(material);

        int radius = blockListener.radius;
        int x_center = blockListener.x_center;
        int y_center = blockListener.y_center;
        int y_offset = blockListener.y_offset;
        int z_center = blockListener.z_center;
        x -= -radius + x_center;
        y -= -radius + y_center - y_offset;
        z -= -radius + z_center;

        broadcastLine("B,0,0,"+x+","+y+","+z+","+type);
        broadcastLine("R,0,0");

        System.out.println("notified block update: ("+x+","+y+","+z+") to "+type);
    }

    public void notifyChat(String message) {
        broadcastLine("T," + message);
    }
}
