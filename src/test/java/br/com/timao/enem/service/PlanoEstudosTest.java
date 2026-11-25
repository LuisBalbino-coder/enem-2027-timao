package br.com.timao.enem.service;

import br.com.timao.enem.model.Payloads;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanoEstudosTest {

    // Terça-feira, dentro da fase 2 para um alvo em 15/02/2027.
    private static final LocalDate HOJE = LocalDate.of(2026, 11, 24);
    private static final LocalDate ALVO_FASE_2 = LocalDate.of(2027, 2, 15);
    private static final LocalDate ALVO_ENEM = LocalDate.of(2027, 11, 7);

    // Sexta-feira que inicia uma semana atravessando a fase 2 -> fase 3 em 01/12/2026.
    private static final LocalDate HOJE_VIRADA = LocalDate.of(2026, 11, 27);
    private static final LocalDate ALVO_VIRADA = LocalDate.of(2027, 1, 28);

    @Test
    @DisplayName("fase 2 prioriza difícil, depois médio e só então fácil na mesma matéria")
    void fase2PriorizaDificuldade() {
        List<Payloads.AulaPlano> aulas = List.of(
                aula(1, "História", "historia", 1, "facil", false),
                aula(2, "História", "historia", 1, "medio", false),
                aula(3, "História", "historia", 1, "dificil", false)
        );

        Payloads.Cronograma cronograma = PlanoEstudos.gerar(HOJE, ALVO_FASE_2, 20, aulas);

        assertEquals(2, cronograma.fase(), "o cenário deve estar na fase de aprofundamento");
        List<String> ordem = cronograma.dias().stream()
                .flatMap(dia -> dia.tarefas().stream())
                .filter(tarefa -> tarefa.tipo().equals("aula"))
                .map(PlanoEstudosTest::dificuldadeDaAula)
                .toList();

        assertEquals(List.of("dificil", "medio", "facil"), ordem);
    }

    @Test
    @DisplayName("fase 2 coloca um simulado de 20 questões no sábado")
    void fase2AdicionaSimuladoNoSabado() {
        Payloads.Cronograma cronograma = PlanoEstudos.gerar(
                HOJE, ALVO_FASE_2, 20, seisPendentes("dificil"));

        Payloads.DiaPlano sabado = dia(cronograma, "2026-11-28");

        assertEquals(2, cronograma.fase());
        assertEquals("simulado", sabado.tipo());
        assertTrue(temTipo(sabado, "aula"), "o sábado ainda deve ter a aula pendente");
        assertEquals(1, contarTipo(sabado, "simulado"));
        assertEquals(20, simuladoDoDia(sabado).questoes().intValue());
    }

    @Test
    @DisplayName("fase 2 mantém o simulado de sábado sem aulas pendentes")
    void fase2MantemSimuladoComFilaVazia() {
        List<List<Payloads.AulaPlano>> cenarios = List.of(
                List.of(),
                List.of(
                        aula(1, "História", "historia", 1, "dificil", true),
                        aula(2, "História", "historia", 1, "medio", true)
                )
        );

        for (List<Payloads.AulaPlano> aulas : cenarios) {
            Payloads.Cronograma cronograma = PlanoEstudos.gerar(HOJE, ALVO_FASE_2, 20, aulas);
            Payloads.DiaPlano sabado = dia(cronograma, "2026-11-28");

            assertEquals(2, cronograma.fase());
            assertEquals("simulado", sabado.tipo());
            assertEquals("Simulado e revisão", sabado.titulo());
            assertEquals(1, contarTipo(sabado, "simulado"));
            assertEquals(20, simuladoDoDia(sabado).questoes().intValue());
            assertTrue(cronograma.dias().stream().noneMatch(d -> temTipo(d, "aula")));
        }
    }

    @Test
    @DisplayName("virada para a fase 3 no meio da semana não agenda aula nova")
    void viradaDeFaseNaoAgendaAulaNova() {
        Payloads.Cronograma cronograma = PlanoEstudos.gerar(
                HOJE_VIRADA, ALVO_VIRADA, 20, seisPendentes("dificil"));

        assertEquals(2, cronograma.fase());
        assertFalse(cronograma.bloqueioNovo());
        assertTrue(temTipo(dia(cronograma, "2026-11-27"), "aula"));

        Payloads.DiaPlano sabado = dia(cronograma, "2026-11-28");
        assertEquals("simulado", sabado.tipo());
        assertEquals(1, contarTipo(sabado, "simulado"));

        for (String data : List.of("2026-12-01", "2026-12-02", "2026-12-03")) {
            Payloads.DiaPlano dia = dia(cronograma, data);
            assertEquals("revisao", dia.tipo(), "dia já está na reta final: " + data);
            assertFalse(temTipo(dia, "aula"), "não pode agendar aula nova em " + data);
        }
    }

    @Test
    @DisplayName("fase 1 não antecipa o simulado de sábado")
    void fase1NaoTemSimulado() {
        Payloads.Cronograma cronograma = PlanoEstudos.gerar(
                HOJE, ALVO_ENEM, 20, seisPendentes("dificil"));

        Payloads.DiaPlano sabado = dia(cronograma, "2026-11-28");

        assertEquals(1, cronograma.fase());
        assertEquals("estudo", sabado.tipo());
        assertFalse(temTipo(sabado, "simulado"));
    }

    private static Payloads.AulaPlano aula(int id, String materia, String materiaSlug, int prioridade,
                                           String dificuldade, boolean concluida) {
        return new Payloads.AulaPlano(
                id, id, "Tópico " + id, "topico-" + id,
                materia, materiaSlug, prioridade, 1, dificuldade,
                "Aula " + id, 20, concluida);
    }

    private static List<Payloads.AulaPlano> seisPendentes(String dificuldade) {
        return List.of(
                aula(1, "História", "historia", 1, dificuldade, false),
                aula(2, "História", "historia", 1, dificuldade, false),
                aula(3, "História", "historia", 1, dificuldade, false),
                aula(4, "História", "historia", 1, dificuldade, false),
                aula(5, "História", "historia", 1, dificuldade, false),
                aula(6, "História", "historia", 1, dificuldade, false)
        );
    }

    private static String dificuldadeDaAula(Payloads.Tarefa tarefa) {
        return switch (tarefa.aulaId().intValue()) {
            case 1 -> "facil";
            case 2 -> "medio";
            case 3 -> "dificil";
            default -> throw new AssertionError("aula inesperada: " + tarefa.aulaId());
        };
    }

    private static Payloads.DiaPlano dia(Payloads.Cronograma cronograma, String data) {
        return cronograma.dias().stream()
                .filter(dia -> dia.data().equals(data))
                .findFirst()
                .orElseThrow(() -> new AssertionError("dia não encontrado: " + data));
    }

    private static boolean temTipo(Payloads.DiaPlano dia, String tipo) {
        return dia.tarefas().stream().anyMatch(tarefa -> tarefa.tipo().equals(tipo));
    }

    private static long contarTipo(Payloads.DiaPlano dia, String tipo) {
        return dia.tarefas().stream().filter(tarefa -> tarefa.tipo().equals(tipo)).count();
    }

    private static Payloads.Tarefa simuladoDoDia(Payloads.DiaPlano dia) {
        return dia.tarefas().stream()
                .filter(tarefa -> tarefa.tipo().equals("simulado"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("sábado sem tarefa de simulado"));
    }
}
