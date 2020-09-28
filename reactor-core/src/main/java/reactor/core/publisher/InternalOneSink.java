/*
 * Copyright (c) 2011-Present VMware Inc. or its affiliates, All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package reactor.core.publisher;

import reactor.core.Exceptions;
import reactor.util.annotation.Nullable;

public interface InternalOneSink<T> extends Sinks.One<T>, InternalEmptySink<T> {

	@Override
	default void emitValue(@Nullable T value, Sinks.EmitStrategy strategy) {
		if (value == null) {
			emitEmpty(strategy);
			return;
		}

		for (;;) {
			Sinks.Emission emission = tryEmitValue(value);
			if (emission.hasSucceeded()) {
				return;
			}

			boolean shouldRetry = strategy.onEmissionFailure(emission);
			if (shouldRetry) {
				continue;
			}

			switch (emission) {
				case FAIL_ZERO_SUBSCRIBER:
					//we want to "discard" without rendering the sink terminated.
					// effectively NO-OP cause there's no subscriber, so no context :(
					return;
				case FAIL_OVERFLOW:
					Operators.onDiscard(value, currentContext());
					//the emitError will onErrorDropped if already terminated
					emitError(Exceptions.failWithOverflow("Backpressure overflow during Sinks.Many#emitNext"));
					return;
				case FAIL_CANCELLED:
					Operators.onDiscard(value, currentContext());
					return;
				case FAIL_TERMINATED:
					Operators.onNextDropped(value, currentContext());
					return;
				case FAIL_NON_SERIALIZED:
					throw new IllegalStateException("Non-serialized access");
				default:
					throw new IllegalStateException("Unknown state: " + emission);
			}
		}
	}
}
