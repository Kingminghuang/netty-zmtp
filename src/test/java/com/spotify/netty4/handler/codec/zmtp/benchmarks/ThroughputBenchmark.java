/*
 * Copyright (c) 2012-2015 Spotify AB
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.spotify.netty4.handler.codec.zmtp.benchmarks;

import com.spotify.netty4.handler.codec.zmtp.ZMTPCodec;
import com.spotify.netty4.handler.codec.zmtp.ZMTPMessage;
import com.spotify.netty4.handler.codec.zmtp.ZMTPProtocol;
import com.spotify.netty4.handler.codec.zmtp.ZMTPSession;
import com.spotify.netty4.util.BatchFlusher;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.MessageSizeEstimator;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.ReferenceCountUtil;

import static com.google.common.base.Strings.repeat;
import static com.spotify.netty4.handler.codec.zmtp.ZMTPSocketType.DEALER;
import static com.spotify.netty4.handler.codec.zmtp.ZMTPSocketType.ROUTER;
import static io.netty.channel.ChannelOption.ALLOCATOR;
import static io.netty.channel.ChannelOption.WRITE_BUFFER_HIGH_WATER_MARK;
import static io.netty.channel.ChannelOption.WRITE_BUFFER_LOW_WATER_MARK;

/**
 * A raw one-way throughput benchmark.
 */
public class ThroughputBenchmark {

  private static final InetSocketAddress ANY_PORT = new InetSocketAddress("127.0.0.1", 0);

  public static void main(final String... args) throws InterruptedException {
    final ProgressMeter meter = new ProgressMeter("requests");

    // Codecs
    final ZMTPCodec serverCodec = ZMTPCodec.of(ZMTPProtocol.ZMTP20.withSocketType(ROUTER));
    final ZMTPCodec clientCodec = ZMTPCodec.of(ZMTPProtocol.ZMTP20.withSocketType(DEALER));

    // Server
    final ServerBootstrap serverBootstrap = new ServerBootstrap()
        .group(new NioEventLoopGroup(1), new NioEventLoopGroup(1))
        .channel(NioServerSocketChannel.class)
        .childOption(ALLOCATOR, PooledByteBufAllocator.DEFAULT)
        .childHandler(new ChannelInitializer<NioSocketChannel>() {
          @Override
          protected void initChannel(final NioSocketChannel ch) throws Exception {
            ch.pipeline().addLast(serverCodec);
            ch.pipeline().addLast(new ServerHandler(meter));
          }
        });
    final Channel server = serverBootstrap.bind(ANY_PORT).awaitUninterruptibly().channel();

    // Client
    final SocketAddress address = server.localAddress();
    final Bootstrap clientBootstrap = new Bootstrap()
        .group(new NioEventLoopGroup(1))
        .channel(NioSocketChannel.class)
        .option(ALLOCATOR, PooledByteBufAllocator.DEFAULT)
        .option(ChannelOption.MESSAGE_SIZE_ESTIMATOR, ByteBufSizeEstimator.INSTANCE)
        .option(WRITE_BUFFER_HIGH_WATER_MARK, 4 * 1024 * 1024)
        .option(WRITE_BUFFER_LOW_WATER_MARK, 2 * 1024 * 1024)
        .handler(new ChannelInitializer<NioSocketChannel>() {
          @Override
          protected void initChannel(final NioSocketChannel ch) throws Exception {
            ch.pipeline().addLast(clientCodec);
            ch.pipeline().addLast(new ClientHandler());
          }
        });
    final Channel client = clientBootstrap.connect(address).awaitUninterruptibly().channel();

    // Run until client is closed
    client.closeFuture().await();
  }

  private static class ServerHandler extends ChannelInboundHandlerAdapter {

    private final ProgressMeter meter;

    public ServerHandler(final ProgressMeter meter) {
      this.meter = meter;
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
      ReferenceCountUtil.release(msg);
      meter.inc(1, 0);
    }
  }

  private static class ClientHandler extends ChannelInboundHandlerAdapter {

    private static final int BATCH_SIZE = 128;

    private static final ZMTPMessage MESSAGE = ZMTPMessage.fromUTF8(repeat(".", 100));

    private BatchFlusher flusher;

    @Override
    public void channelRegistered(final ChannelHandlerContext ctx) throws Exception {
      this.flusher = new BatchFlusher(ctx.channel());
    }

    @Override
    public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt)
        throws Exception {
      if (evt instanceof ZMTPSession) {
        send(ctx);
      }
    }

    @Override
    public void channelWritabilityChanged(final ChannelHandlerContext ctx) throws Exception {
      send(ctx);
    }

    private void send(final ChannelHandlerContext ctx) {
      while(ctx.channel().isWritable()) {
        for (int i = 0; i < BATCH_SIZE; i++) {
          ctx.write(MESSAGE.retain());
        }
        flusher.flush();
      }
    }
  }

  private static class ByteBufSizeEstimator implements MessageSizeEstimator,
                                                       MessageSizeEstimator.Handle {

    public static final ByteBufSizeEstimator INSTANCE = new ByteBufSizeEstimator();

    @Override
    public Handle newHandle() {
      return this;
    }

    @Override
    public int size(final Object msg) {
      if (msg instanceof ByteBuf) {
        return ((ByteBuf) msg).readableBytes();
      }
      return 0;
    }
  }
}
