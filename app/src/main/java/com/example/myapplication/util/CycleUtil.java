package com.example.myapplication.util;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Helper za rad sa ciklusima rang listi (nedeljni i mesečni).
 *
 * Ciklus se identifikuje stabilnim ID-jem koji se koristi kao ime dokumenta u
 * Firestore-u:
 *   - nedeljni:  "weekly_2026-W24"   (ISO nedelja, počinje ponedeljkom)
 *   - mesečni:   "monthly_2026-06"
 */
public final class CycleUtil {

    public static final String TYPE_WEEKLY  = "weekly";
    public static final String TYPE_MONTHLY = "monthly";

    private CycleUtil() {}

    private static Calendar isoCalendar() {
        Calendar c = Calendar.getInstance(Locale.getDefault());
        c.setFirstDayOfWeek(Calendar.MONDAY);
        c.setMinimalDaysInFirstWeek(4); // ISO-8601
        return c;
    }

    // ---------------------------------------------------------------- WEEKLY

    public static String getWeeklyCycleId(Date date) {
        Calendar c = isoCalendar();
        c.setTime(date);
        int week = c.get(Calendar.WEEK_OF_YEAR);
        int year = c.get(Calendar.YEAR);
        // Krajem decembra nedelja može pripadati sledećoj godini (ISO).
        if (c.get(Calendar.MONTH) == Calendar.DECEMBER && week == 1) {
            year++;
        }
        return String.format(Locale.US, "%s_%d-W%02d", TYPE_WEEKLY, year, week);
    }

    public static String getCurrentWeeklyCycleId() {
        return getWeeklyCycleId(new Date());
    }

    public static String getPreviousWeeklyCycleId() {
        Calendar c = isoCalendar();
        c.add(Calendar.WEEK_OF_YEAR, -1);
        return getWeeklyCycleId(c.getTime());
    }

    // --------------------------------------------------------------- MONTHLY

    public static String getMonthlyCycleId(Date date) {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM", Locale.US);
        return TYPE_MONTHLY + "_" + fmt.format(date);
    }

    public static String getCurrentMonthlyCycleId() {
        return getMonthlyCycleId(new Date());
    }

    public static String getPreviousMonthlyCycleId() {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.MONTH, -1);
        return getMonthlyCycleId(c.getTime());
    }

    // ----------------------------------------------------------------- MISC

    public static boolean isMonthly(String cycleId) {
        return cycleId != null && cycleId.startsWith(TYPE_MONTHLY);
    }

    /** Opseg datuma ciklusa u čitljivom formatu, npr. "08.06 - 14.06.2026." */
    public static String getDateRangeLabel(String cycleId) {
        Calendar[] range = getRange(cycleId);
        if (range == null) return "";
        SimpleDateFormat dayMonth = new SimpleDateFormat("dd.MM", Locale.getDefault());
        SimpleDateFormat full     = new SimpleDateFormat("dd.MM.yyyy.", Locale.getDefault());
        return dayMonth.format(range[0].getTime()) + " - " + full.format(range[1].getTime());
    }

    /** Vraća [pocetak, kraj] ciklusa kao Calendar parove, ili null ako ID nije validan. */
    public static Calendar[] getRange(String cycleId) {
        if (cycleId == null) return null;
        try {
            if (isMonthly(cycleId)) {
                String ym = cycleId.substring((TYPE_MONTHLY + "_").length()); // "2026-06"
                int year  = Integer.parseInt(ym.substring(0, 4));
                int month = Integer.parseInt(ym.substring(5, 7)) - 1;

                Calendar start = Calendar.getInstance();
                start.clear();
                start.set(year, month, 1, 0, 0, 0);

                Calendar end = (Calendar) start.clone();
                end.set(Calendar.DAY_OF_MONTH, end.getActualMaximum(Calendar.DAY_OF_MONTH));
                setEndOfDay(end);
                return new Calendar[]{start, end};
            } else {
                String yw = cycleId.substring((TYPE_WEEKLY + "_").length()); // "2026-W24"
                int year = Integer.parseInt(yw.substring(0, 4));
                int week = Integer.parseInt(yw.substring(6));

                Calendar start = isoCalendar();
                start.clear();
                start.set(Calendar.YEAR, year);
                start.set(Calendar.WEEK_OF_YEAR, week);
                start.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
                start.set(Calendar.HOUR_OF_DAY, 0);
                start.set(Calendar.MINUTE, 0);
                start.set(Calendar.SECOND, 0);

                Calendar end = (Calendar) start.clone();
                end.add(Calendar.DAY_OF_YEAR, 6);
                setEndOfDay(end);
                return new Calendar[]{start, end};
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static void setEndOfDay(Calendar c) {
        c.set(Calendar.HOUR_OF_DAY, 23);
        c.set(Calendar.MINUTE, 59);
        c.set(Calendar.SECOND, 59);
        c.set(Calendar.MILLISECOND, 999);
    }
}
