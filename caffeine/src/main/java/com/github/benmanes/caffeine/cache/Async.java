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

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

import javax.annotation.Nullable;

/**
 * Static utility methods and classes pertaining to asynchronous operations.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
final class Async {

  private Async() {}

  /** Returns if the future has successfully completed. */
  static boolean isReady(@Nullable CompletableFuture<?> future) {
    return (future != null) && future.isDone() && !future.isCompletedExceptionally();
  }

  /** Returns the current value or null if either not done or failed. */
  static @Nullable <V> V getIfReady(@Nullable CompletableFuture<V> future) {
    return isReady(future) ? future.join() : null;
  }

  /** Returns the value when completed successfully or null if failed. */
  static @Nullable <V> V getWhenSuccessful(@Nullable CompletableFuture<V> future) {
    try {
      return (future == null) ? null : future.get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return null;
    } catch (ExecutionException e) {
      return null;
    }
  }

  /**
   * A removal listener that asynchronously forwards the value stored in a {@link CompletableFuture}
   * if successful to the user-supplied removal listener.
   */
  static final class AsyncRemovalListener<K, V>
      implements RemovalListener<K, CompletableFuture<V>>, Serializable {
    private static final long serialVersionUID = 1L;

    final RemovalListener<K, V> delegate;
    final Executor executor;

    AsyncRemovalListener(RemovalListener<K, V> delegate, Executor executor) {
      this.delegate = requireNonNull(delegate);
      this.executor = requireNonNull(executor);
    }

    @Override
    public void onRemoval(RemovalNotification<K, CompletableFuture<V>> notification) {
      notification.getValue().thenAcceptAsync(value -> {
        delegate.onRemoval(new RemovalNotification<K, V>(
            notification.getKey(), value, notification.getCause()));
      }, executor);
    }

    Object writeReplace() {
      return delegate;
    }
  }

  /**
   * A weigher for asynchronous computations. When the value is being loaded this weigher returns
   * {@code 0} to indicate that the entry should not be evicted due to a size constraint. If the
   * value is computed successfully the entry must be reinserted so that the weight is updated and
   * the expiration timeouts reflect the value once present. This can be done safely using
   * {@link Map#replace(Object, Object, Object)}.
   */
  static final class AsyncWeigher<K, V> implements Weigher<K, CompletableFuture<V>>, Serializable {
    private static final long serialVersionUID = 1L;

    final Weigher<K, V> delegate;

    AsyncWeigher(Weigher<K, V> delegate) {
      this.delegate = requireNonNull(delegate);
    }

    @Override
    public int weigh(K key, CompletableFuture<V> future) {
      return isReady(future) ? delegate.weigh(key, future.join()) : 0;
    }

    Object writeReplace() {
      return delegate;
    }
  }
}
