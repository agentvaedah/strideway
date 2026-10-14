package id.web.agentvaedah.strideway.core.fuel

import id.web.agentvaedah.strideway.core.model.Sport
import kotlin.math.roundToInt

data class FuelPlan(
    val fluidMlPerHour: Int,
    val carbGramsPerHour: Int,
    val drinkEveryMin: Int,
    /** 0 when the session is short enough to skip food. */
    val eatEveryMin: Int,
    val electrolytes: Boolean,
    val totalFluidMl: Int,
    val totalCarbGrams: Int,
)

/**
 * Rule-of-thumb fuelling from common sports-nutrition guidance: roughly 400-800 ml fluid per hour rising
 * with heat, 30-60 g carbohydrate per hour beyond an hour and up to ~80 g on very long efforts, and
 * electrolytes when it is long or hot. A starting point to adjust by thirst and sweat, not a prescription.
 */
object FuelPlanner {
    fun plan(sport: Sport, durationMin: Int, tempC: Double?, weightKg: Double): FuelPlan {
        val t = tempC ?: 27.0
        val base = when {
            t >= 32 -> 750
            t >= 28 -> 650
            t >= 22 -> 550
            else -> 450
        }
        // Heavier riders sweat more; runners lose a little more per hour than cyclists at the same heat.
        val weightAdj = ((weightKg - 70.0) * 4).roundToInt()
        val sportAdj = if (sport == Sport.RUN) 50 else 0
        val fluid = (base + weightAdj + sportAdj).coerceIn(400, 1000)

        val carbs = when {
            durationMin < 60 -> 0
            durationMin < 120 -> 30
            durationMin < 240 -> 60
            else -> 80
        }
        val drinkEvery = when {
            t >= 32 -> 10
            t >= 28 -> 15
            else -> 20
        }
        val eatEvery = when {
            carbs == 0 -> 0
            carbs >= 60 -> 30
            else -> 45
        }
        val hours = durationMin / 60.0
        return FuelPlan(
            fluidMlPerHour = fluid,
            carbGramsPerHour = carbs,
            drinkEveryMin = drinkEvery,
            eatEveryMin = eatEvery,
            electrolytes = durationMin >= 120 || t >= 30,
            totalFluidMl = (fluid * hours).roundToInt(),
            totalCarbGrams = (carbs * hours).roundToInt(),
        )
    }
}

enum class FuelReminder { DRINK, EAT }

/** Fires on moving time, so a café stop does not trigger a drink reminder. One reminder per interval. */
class FuelReminders(private val drinkEveryMin: Int, private val eatEveryMin: Int) {
    private var lastDrinkSlot = 0L
    private var lastEatSlot = 0L

    fun due(movingMs: Long): FuelReminder? {
        val minutes = movingMs / 60_000L
        val eatSlot = if (eatEveryMin > 0) minutes / eatEveryMin else 0L
        val drinkSlot = if (drinkEveryMin > 0) minutes / drinkEveryMin else 0L
        val eat = eatSlot > lastEatSlot
        val drink = drinkSlot > lastDrinkSlot
        if (eat) lastEatSlot = eatSlot
        if (drink) lastDrinkSlot = drinkSlot
        return when {
            eat -> FuelReminder.EAT
            drink -> FuelReminder.DRINK
            else -> null
        }
    }
}
