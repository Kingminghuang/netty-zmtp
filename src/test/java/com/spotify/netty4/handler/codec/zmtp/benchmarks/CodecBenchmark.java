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

package com.spotify.netty4.handler.codec.zmtp.benchmarks;

import com.spotify.netty4.handler.codec.zmtp.ZMTPIncomingMessageDecoder;
import com.spotify.netty4.handler.codec.zmtp.ZMTPMessage;
import com.spotify.netty4.handler.codec.zmtp.ZMTPMessageDecoder;
import com.spotify.netty4.handler.codec.zmtp.ZMTPMessageParser;
import com.spotify.netty4.handler.codec.zmtp.ZMTPMessageParsingException;
import com.spotify.netty4.handler.codec.zmtp.ZMTPUtils;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.util.ReferenceCountUtil;

@State(Scope.Benchmark)
public class CodecBenchmark {

  private final ZMTPMessage message = ZMTPMessage.fromStringsUTF8(
      true,
      "first identity frame",
      "second identity frame",
      "",
      "datadatadatadatadatadatadatadatadatadata",
      "datadatadatadatadatadatadatadatadatadata",
      "datadatadatadatadatadatadatadatadatadata",
      "datadatadatadatadatadatadatadatadatadata");

  private final ZMTPMessageParser messageParserV1 =
      ZMTPMessageParser.create(1, new ZMTPIncomingMessageDecoder(true, Integer.MAX_VALUE));

  private final ZMTPMessageParser messageParserV2 =
      ZMTPMessageParser.create(2, new ZMTPIncomingMessageDecoder(true, Integer.MAX_VALUE));

  private final ZMTPMessageParser discardingParserV1 =
      ZMTPMessageParser.create(1, new Discarder());

  private final ZMTPMessageParser discardingParserV2 =
      ZMTPMessageParser.create(2, new Discarder());

  private final ByteBuf incomingV1;
  private final ByteBuf incomingV2;

  private final ByteBuf tmp = PooledByteBufAllocator.DEFAULT.buffer(4096);

  {
    incomingV1 = PooledByteBufAllocator.DEFAULT.buffer(ZMTPUtils.messageSize(message, true, 1));
    incomingV2 = PooledByteBufAllocator.DEFAULT.buffer(ZMTPUtils.messageSize(message, true, 2));
    ZMTPUtils.writeMessage(message, incomingV1, true, 1);
    ZMTPUtils.writeMessage(message, incomingV2, true, 2);
  }

  @Benchmark
  public Object parsingToMessageV1() throws ZMTPMessageParsingException {
    final Object parsed = messageParserV1.parse(incomingV1.resetReaderIndex());
    ReferenceCountUtil.release(parsed);
    return parsed;
  }

  @Benchmark
  public Object parsingToMessageV2() throws ZMTPMessageParsingException {
    final Object parsed = messageParserV2.parse(incomingV2.resetReaderIndex());
    ReferenceCountUtil.release(parsed);
    return parsed;
  }

  @Benchmark
  public Object discardingV1() throws ZMTPMessageParsingException {
    return discardingParserV1.parse(incomingV1.resetReaderIndex());
  }

  @Benchmark
  public Object discardingV2() throws ZMTPMessageParsingException {
    return discardingParserV2.parse(incomingV2.resetReaderIndex());
  }

  @Benchmark
  public Object encodingV1() throws ZMTPMessageParsingException {
    ZMTPUtils.writeMessage(message, tmp.setIndex(0, 0), true, 1);
    return tmp;
  }

  @Benchmark
  public Object encodingV2() throws ZMTPMessageParsingException {
    ZMTPUtils.writeMessage(message, tmp.setIndex(0, 0), true, 2);
    return tmp;
  }

  public static void main(final String... args) throws RunnerException, InterruptedException {
    Options opt = new OptionsBuilder()
        .include(".*")
        .forks(1)
        .build();

    new Runner(opt).run();
  }


  private class Discarder implements ZMTPMessageDecoder {

    private int size;


    @Override
    public void header(final int length, final boolean more) {
      this.size += size;
    }

    @Override
    public void content(final ByteBuf data) {
      data.skipBytes(data.readableBytes());
    }

    @Override
    public Integer finish() {
      return size;
    }
  }
}
