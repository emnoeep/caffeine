/*
 * Copyright 2015 Ben Manes. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.benmanes.caffeine.cache;

import static java.util.Objects.requireNonNull;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.nullValue;

import java.util.function.Consumer;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.github.benmanes.caffeine.ConcurrentTestHarness;
import com.google.common.base.MoreObjects;

/**
 * @author ben.manes@gmail.com (Ben Manes)
 */
public final class StripedBufferTest {
  static final Integer ELEMENT = 1;

  @Test(dataProvider = "buffers")
  public void init(FakeBuffer<Integer> buffer) {
    assertThat(buffer.table, is(nullValue()));

    buffer.offer(ELEMENT);
    assertThat(buffer.table.length, is(1));
  }

  @Test(dataProvider = "buffers")
  public void produce(FakeBuffer<Integer> buffer) {
    ConcurrentTestHarness.timeTasks(10, () -> {
      for (int i = 0; i < 10; i++) {
        buffer.offer(ELEMENT);
        Thread.yield();
      }
    });
    assertThat(buffer.table.length, lessThanOrEqualTo(StripedBuffer.MAXIMUM_TABLE_SIZE));
  }

  @Test(dataProvider = "buffers")
  public void drain(FakeBuffer<Integer> buffer) {
    buffer.drainTo(e -> {});
    assertThat(buffer.drains, is(0));

    // Expand and drain
    buffer.offer(ELEMENT);
    buffer.drainTo(e -> {});
    assertThat(buffer.drains, is(1));
  }

  @DataProvider
  public Object[][] buffers() {
    return new Object[][] {
        { new FakeBuffer<Integer>(Buffer.FULL) },
        { new FakeBuffer<Integer>(Buffer.FAILED) },
        { new FakeBuffer<Integer>(Buffer.SUCCESS) },
    };
  }

  static int ceilingNextPowerOfTwo(int x) {
    return 1 << (Integer.SIZE - Integer.numberOfLeadingZeros(x - 1));
  }

  static final class FakeBuffer<E> extends StripedBuffer<E> {
    final int result;
    int drains = 0;

    FakeBuffer(int result) {
      this.result = requireNonNull(result);
    }

    @Override protected Buffer<E> create(E e) {
      return new Buffer<E>() {
        @Override public int offer(E e) {
          return result;
        }
        @Override public void drainTo(Consumer<E> consumer) {
          drains++;
        }
        @Override public int size() {
          return 0;
        }
        @Override public int reads() {
          return 0;
        }
        @Override public int writes() {
          return 0;
        }
      };
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this).addValue(result).toString();
    }
  }
}
