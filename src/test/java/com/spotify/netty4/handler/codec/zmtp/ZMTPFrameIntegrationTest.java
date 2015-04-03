/*
 * Copyright (c) 2012-2013 Spotify AB
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

package com.spotify.netty4.handler.codec.zmtp;

import org.junit.Test;
import org.zeromq.ZFrame;
import org.zeromq.ZMQ;
import org.zeromq.ZMsg;

import java.nio.ByteBuffer;
import java.util.UUID;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;

import static org.junit.Assert.assertEquals;

public class ZMTPFrameIntegrationTest {

  @Test
  public void testOneFrameREP() {
    final UUID remoteId = UUID.randomUUID();
    final ZFrame f = new ZFrame("test-frame".getBytes());

    final ZMTPTestConnector tester = new ZMTPTestConnector() {
      @Override
      public void preConnect(final ZMQ.Socket socket) {
        socket.setIdentity(encodeUUID(remoteId));
      }

      @Override
      public void afterConnect(final ZMQ.Socket socket, final ChannelFuture future) {
        f.sendAndKeep(socket);
      }

      @Override
      protected void onConnect(final ZMTPSession session) {
        // Verify that we can parse the identity correctly
        assertEquals(ByteBuffer.wrap(encodeUUID(remoteId)), session.remoteIdentity());
      }

      @Override
      public boolean onMessage(final ZMTPIncomingMessage msg) {
        // Verify that frames received is correct
        assertEquals(2, msg.message().size());
        assertEquals(Unpooled.EMPTY_BUFFER, msg.message().frame(0));
        assertEquals(Unpooled.wrappedBuffer(f.getData()), msg.message().frame(1));

        f.destroy();

        return true;
      }
    };

    assertEquals(true, tester.connectAndReceive(ZMQ.REQ));
  }

  @Test
  public void testMultipleFrameREP() {
    final UUID remoteId = UUID.randomUUID();

    final ZMTPTestConnector tester = new ZMTPTestConnector() {
      ZMsg m;

      @Override
      public void preConnect(final ZMQ.Socket socket) {
        m = new ZMsg();

        for (int i = 0; i < 16; i++) {
          m.addString("test-frame-" + i);
        }

        socket.setIdentity(encodeUUID(remoteId));
      }

      @Override
      public void afterConnect(final ZMQ.Socket socket, final ChannelFuture future) {
        m.duplicate().send(socket);
      }


      @Override
      protected void onConnect(final ZMTPSession session) {
        // Verify that we can parse the identity correctly
        assertEquals(ByteBuffer.wrap(encodeUUID(remoteId)), session.remoteIdentity());
      }

      @Override
      public boolean onMessage(final ZMTPIncomingMessage msg) {

        // Verify that frames received is correct
        assertEquals(m.size() + 1, msg.message().size());

        int i = 1;
        for (final ZFrame f : m) {
          assertEquals(Unpooled.wrappedBuffer(f.getData()),
                       msg.message().frame(i));
          i++;
        }

        return true;
      }
    };

    assertEquals(true, tester.connectAndReceive(ZMQ.REQ));
  }

  private static byte[] encodeUUID(final UUID uuid) {
    final long most = uuid.getMostSignificantBits();
    final long least = uuid.getLeastSignificantBits();

    return new byte[]{
        (byte) (most >>> 56), (byte) (most >>> 48), (byte) (most >>> 40),
        (byte) (most >>> 32), (byte) (most >>> 24), (byte) (most >>> 16),
        (byte) (most >>> 8), (byte) most,
        (byte) (least >>> 56), (byte) (least >>> 48), (byte) (least >>> 40),
        (byte) (least >>> 32), (byte) (least >>> 24), (byte) (least >>> 16),
        (byte) (least >>> 8), (byte) least};
  }
}
