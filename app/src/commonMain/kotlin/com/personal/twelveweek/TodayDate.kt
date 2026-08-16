package com.personal.twelveweek

/** The device's local calendar date as "yyyy-MM-dd" — always local time,
 *  never UTC (a session finished at 11pm local must count for that local
 *  day, not roll to UTC's next day). Used to key [StreakTracker]'s active
 *  days. */
expect fun todayIso(): String
