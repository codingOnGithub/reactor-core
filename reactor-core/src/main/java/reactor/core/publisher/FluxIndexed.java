/*
 * Copyright (c) 2011-2017 Pivotal Software Inc, All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package reactor.core.publisher;

import java.util.Objects;
import java.util.function.BiFunction;

import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.util.annotation.Nullable;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

/**
 * @author Simon Baslé
 */
public class FluxIndexed<T, I> extends FluxOperator<T, Tuple2<I, T>> {

	private final BiFunction<? super Long, ? super T, ? extends I> indexMapper;

	public FluxIndexed(Flux<T> source,
			BiFunction<? super Long, ? super T, ? extends I> indexMapper) {
		super(source);
		this.indexMapper = Objects.requireNonNull(indexMapper, "indexMapper must be non null");
	}

	@Override
	public void subscribe(CoreSubscriber<? super Tuple2<I, T>> actual) {
		source.subscribe(new FluxIndexedSubscriber<>(actual, indexMapper));
	}

	static final class FluxIndexedSubscriber<I, T> implements InnerOperator<T, Tuple2<I, T>> {

		final CoreSubscriber<? super Tuple2<I, T>>             actual;
		final BiFunction<? super Long, ? super T, ? extends I> indexMapper;

		boolean      done;
		Subscription s;
		long         index = 0;

		public FluxIndexedSubscriber(CoreSubscriber<? super Tuple2<I, T>> actual,
				BiFunction<? super Long, ? super T, ? extends I> indexMapper) {
			this.actual = actual;
			this.indexMapper = indexMapper;
		}

		@Override
		public void onSubscribe(Subscription s) {
			if (Operators.validate(this.s, s)) {
				this.s = s;

				actual.onSubscribe(this);
			}
		}

		@Override
		public void onNext(T t) {
			if (done) {
				Operators.onNextDropped(t, actual.currentContext());
				return;
			}

			long i = this.index;
			try {
				I typedIndex = Objects.requireNonNull(indexMapper.apply(i, t),
						"indexMapper returned a null value at raw index " + i +
								" for value " + t);
				this.index = i + 1L;
				actual.onNext(Tuples.of(typedIndex, t));
			}
			catch (Throwable e) {
				onError(Operators.onOperatorError(s, e, t, actual.currentContext()));
			}
		}

		@Override
		public void onError(Throwable t) {
			if (done) {
				Operators.onErrorDropped(t, actual.currentContext());
				return;
			}

			done = true;

			actual.onError(t);
		}

		@Override
		public void onComplete() {
			if (done) {
				return;
			}
			done = true;

			actual.onComplete();
		}

		@Override
		public CoreSubscriber<? super Tuple2<I, T>> actual() {
			return this.actual;
		}

		@Override
		public void request(long n) {
			s.request(n);
		}

		@Override
		public void cancel() {
			s.cancel();
		}

		@Override
		@Nullable
		public Object scanUnsafe(Attr key) {
			if (key == Attr.PARENT) return s;
			if (key == Attr.TERMINATED) return done;

			return InnerOperator.super.scanUnsafe(key);
		}
	}
}
