package br.com.timao.enem.service;

import br.com.timao.enem.model.Payloads;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Map;

/** Contagem regressiva, fases e calendário até o ENEM. */
public final class Calendario {

    private static final double JANELA_DIAS = 730.0;

    private static final Map<Integer, String> MESES = Map.ofEntries(
            Map.entry(1, "Janeiro"), Map.entry(2, "Fevereiro"), Map.entry(3, "Março"),
            Map.entry(4, "Abril"), Map.entry(5, "Maio"), Map.entry(6, "Junho"),
            Map.entry(7, "Julho"), Map.entry(8, "Agosto"), Map.entry(9, "Setembro"),
            Map.entry(10, "Outubro"), Map.entry(11, "Novembro"), Map.entry(12, "Dezembro"));

    private Calendario() {
    }

    public static LocalDate inicioAprofundamento(LocalDate alvo) {
        return alvo.minusMonths(3).withDayOfMonth(1);
    }

    public static LocalDate inicioRetaFinal(LocalDate alvo) {
        return alvo.minusMonths(1).withDayOfMonth(1);
    }

    public static int numeroFase(LocalDate data, LocalDate alvo) {
        if (!data.isBefore(inicioRetaFinal(alvo))) {
            return 3;
        }
        if (!data.isBefore(inicioAprofundamento(alvo))) {
            return 2;
        }
        return 1;
    }

    public static Payloads.Fase fase(LocalDate hoje, LocalDate alvo) {
        int numero = numeroFase(hoje, alvo);
        if (numero == 3) {
            return new Payloads.Fase(3, "Reta final",
                    "Revisão rápida: apenas temas já estudados, flashcards e erros anteriores.",
                    alvo.toString());
        }
        if (numero == 2) {
            return new Payloads.Fase(2, "Aprofundamento",
                    "Questões médias e difíceis, simulados cronometrados e redação intensiva.",
                    inicioRetaFinal(alvo).minusDays(1).toString());
        }
        return new Payloads.Fase(1, "Base e construção",
                "Teoria completa de História e Geografia, repertório de redação e fundamentos de Matemática.",
                inicioAprofundamento(alvo).minusDays(1).toString());
    }

    public static int urgencia(long diasRestantes) {
        double indice = 100.0 * (1.0 - (diasRestantes / JANELA_DIAS));
        return (int) Math.max(0, Math.min(100, Math.round(indice)));
    }

    public static long diasRestantes(LocalDate hoje, LocalDate alvo) {
        return Math.max(0, ChronoUnit.DAYS.between(hoje, alvo));
    }

    public static String tituloMes(YearMonth mes) {
        return MESES.get(mes.getMonthValue()) + " de " + mes.getYear();
    }

    /** Nome do dia da semana em português, com inicial minúscula. */
    public static String diaSemana(LocalDate data) {
        return data.getDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL, new Locale("pt", "BR"));
    }
}
