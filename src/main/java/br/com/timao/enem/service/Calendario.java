package br.com.timao.enem.service;

import br.com.timao.enem.model.Payloads;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Contagem regressiva e curva de urgência até o ENEM. */
public final class Calendario {

    private static final double JANELA_DIAS = 730.0;

    private Calendario() {
    }

    public static Payloads.Fase fase(LocalDate hoje, LocalDate alvo) {
        LocalDate inicioAprofundamento = alvo.minusMonths(3).withDayOfMonth(1);
        LocalDate inicioRetaFinal = alvo.minusMonths(1).withDayOfMonth(1);

        if (!hoje.isBefore(inicioRetaFinal)) {
            return new Payloads.Fase(3, "Reta final",
                    "Revisão rápida: apenas temas já estudados, flashcards e erros anteriores.",
                    alvo.toString());
        }
        if (!hoje.isBefore(inicioAprofundamento)) {
            return new Payloads.Fase(2, "Aprofundamento",
                    "Questões médias e difíceis, simulados cronometrados e redação intensiva.",
                    inicioRetaFinal.minusDays(1).toString());
        }
        return new Payloads.Fase(1, "Base e construção",
                "Teoria completa de História e Geografia, repertório de redação e fundamentos de Matemática.",
                inicioAprofundamento.minusDays(1).toString());
    }

    public static int urgencia(long diasRestantes) {
        double indice = 100.0 * (1.0 - (diasRestantes / JANELA_DIAS));
        return (int) Math.max(0, Math.min(100, Math.round(indice)));
    }

    public static long diasRestantes(LocalDate hoje, LocalDate alvo) {
        return Math.max(0, ChronoUnit.DAYS.between(hoje, alvo));
    }
}
