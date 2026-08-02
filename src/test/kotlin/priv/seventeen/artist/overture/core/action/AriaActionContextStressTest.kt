/*
 * Copyright 2026 17Artist
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

package priv.seventeen.artist.overture.core.action

import priv.seventeen.artist.aria.Aria
import priv.seventeen.artist.aria.annotation.java.AriaInvokeHandler
import priv.seventeen.artist.aria.callable.CallableManager
import priv.seventeen.artist.aria.callable.InvocationData
import priv.seventeen.artist.aria.value.IValue
import priv.seventeen.artist.aria.value.NoneValue
import priv.seventeen.artist.aria.value.NumberValue
import priv.seventeen.artist.aria.value.StoreOnlyValue
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AriaActionContextStressTest {
    @Test
    fun `fresh contexts expose only independent item and player object targets`() {
        CallableManager.INSTANCE.registerObjectFunction(ContextProbeFunctions::class.java)
        val routine = Aria.compile(
            "overture.context-stress",
            "return val.item.contextItemId() * 1000000 + val.player.contextPlayerId()"
        )

        val firstItem = ContextItemProbe(7)
        val firstPlayer = ContextPlayerProbe(11)
        val first = AriaActionContextFactory.createWithValues(firstItem, firstPlayer)
        assertSame(NoneValue.NONE, first.self)
        assertSame(
            firstItem,
            (first.getLocalValue(AriaActionContextFactory.ITEM_KEY).ariaValue() as StoreOnlyValue<*>).jvmValue()
        )
        assertSame(
            firstPlayer,
            (first.getLocalValue(AriaActionContextFactory.PLAYER_KEY).ariaValue() as StoreOnlyValue<*>).jvmValue()
        )
        assertEquals(7_000_011.0, routine.execute(first).numberValue())

        repeat(SEQUENTIAL_CONTEXTS) { index ->
            val context = AriaActionContextFactory.createWithValues(
                ContextItemProbe(index),
                ContextPlayerProbe(SEQUENTIAL_CONTEXTS - index)
            )
            assertEquals(
                index * 1_000_000.0 + SEQUENTIAL_CONTEXTS - index,
                routine.execute(context).numberValue()
            )
        }

        val failures = ConcurrentLinkedQueue<String>()
        val executor = Executors.newFixedThreadPool(PARALLEL_WORKERS)
        try {
            val futures = (0 until PARALLEL_WORKERS).map { worker ->
                executor.submit {
                    repeat(CONTEXTS_PER_WORKER) { index ->
                        val itemId = worker * CONTEXTS_PER_WORKER + index
                        val playerId = 900_000 - itemId
                        val context = AriaActionContextFactory.createWithValues(
                            ContextItemProbe(itemId),
                            ContextPlayerProbe(playerId)
                        )
                        val expected = itemId * 1_000_000.0 + playerId
                        val actual = routine.execute(context).numberValue()
                        if (actual != expected) {
                            failures.add("worker=$worker index=$index expected=$expected actual=$actual")
                        }
                    }
                }
            }
            futures.forEach { it.get(30, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }

        assertTrue(
            failures.isEmpty(),
            "context values leaked across executions: ${failures.peek()}"
        )
    }

    private companion object {
        const val SEQUENTIAL_CONTEXTS = 10_000
        const val PARALLEL_WORKERS = 8
        const val CONTEXTS_PER_WORKER = 2_500
    }
}

private data class ContextItemProbe(val id: Int)
private data class ContextPlayerProbe(val id: Int)

private object ContextProbeFunctions {
    @JvmStatic
    @AriaInvokeHandler(value = "contextItemId", target = ContextItemProbe::class)
    fun contextItemId(data: InvocationData): IValue<*> =
        NumberValue((data.target as ContextItemProbe).id.toDouble())

    @JvmStatic
    @AriaInvokeHandler(value = "contextPlayerId", target = ContextPlayerProbe::class)
    fun contextPlayerId(data: InvocationData): IValue<*> =
        NumberValue((data.target as ContextPlayerProbe).id.toDouble())
}
