package br.com.timao.enem.service;

import br.com.timao.enem.model.Payloads;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cronograma semanal determinístico:
 * - Fase 1/2: intercala matérias por peso de prioridade, uma aula por dia útil.
 * - Fase 2: prioriza temas médios/difíceis e inclui simulado no sábado.
 * - Fase 3: bloqueia conteúdo novo e agenda apenas revisão do que já foi estudado.
 */
public final class PlanoEstudos {

    private PlanoEstudos() {
    }

    public static Payloads.Cronograma gerar(LocalDate hoje, LocalDate alvo, int metaQuestoes,
                                            List<Payloads.AulaPlano> aulas) {
        int fase = Calendario.numeroFase(hoje, alvo);
        boolean bloqueio = fase >= 3;
        List<Payloads.DiaPlano> dias = new ArrayList<>();
        Set<Integer> topicosSemana = new LinkedHashSet<>();

        List<Payloads.AulaPlano> pendentes = new ArrayList<>();
        for (Payloads.AulaPlano aula : aulas) {
            if (!aula.concluida()) {
                pendentes.add(aula);
            }
        }
        List<Fila> filas = montarFilas(pendentes, fase);
        int questoesPorDia = Math.max(5, Math.min(20, metaQuestoes / 2));

        List<Payloads.AulaPlano> revisar = new ArrayList<>();
        if (bloqueio) {
            Map<Integer, Payloads.AulaPlano> vistos = new LinkedHashMap<>();
            for (Payloads.AulaPlano aula : aulas) {
                if (aula.concluida()) {
                    vistos.putIfAbsent(aula.topicoId(), aula);
                }
            }
            revisar.addAll(vistos.values());
        }

        for (int i = 0; i < 7; i++) {
            LocalDate data = hoje.plusDays(i);
            List<Payloads.Tarefa> tarefas = new ArrayList<>();
            String tipo;
            String titulo;
            String foco = "Geral";

            if (bloqueio) {
                tipo = "revisao";
                if (revisar.isEmpty()) {
                    titulo = "Revisão dos seus resumos";
                    tarefas.add(new Payloads.Tarefa("revisao", null,
                            "Revisar resumos e mapas mentais já estudados", null, null,
                            "Geral", null, null, null));
                } else {
                    Payloads.AulaPlano tema = revisar.get(i % revisar.size());
                    titulo = tema.topico();
                    foco = tema.materia();
                    tarefas.add(new Payloads.Tarefa("revisao", null,
                            "Revisar o resumo de " + tema.topico(), tema.topico(), tema.topicoSlug(),
                            tema.materia(), tema.materiaSlug(), null, null));
                    tarefas.add(new Payloads.Tarefa("revisao", null,
                            "Flashcards de " + tema.materia(), tema.topico(), tema.topicoSlug(),
                            tema.materia(), tema.materiaSlug(), null, null));
                }
                tarefas.add(questoes("Refazer questões erradas", "Geral", null, metaQuestoes));
            } else if (data.getDayOfWeek() == DayOfWeek.SUNDAY) {
                tipo = "revisao";
                titulo = "Fechar a semana";
                foco = "Revisão";
                tarefas.add(new Payloads.Tarefa("revisao", null,
                        "Revisar flashcards dos temas da semana", null, null, "Geral", null, null, null));
                if (!topicosSemana.isEmpty()) {
                    tarefas.add(new Payloads.Tarefa("revisao", null,
                            "Reler os resumos dos temas estudados", null, null, "Geral", null, null, null));
                }
                tarefas.add(questoes("Refazer questões erradas", "Geral", null, metaQuestoes));
            } else {
                Payloads.AulaPlano aula = proxima(filas);
                if (aula == null) {
                    tipo = "revisao";
                    titulo = "Revisão e questões";
                    foco = "Revisão";
                    tarefas.add(new Payloads.Tarefa("revisao", null,
                            "Revisar os temas já estudados", null, null, "Geral", null, null, null));
                    tarefas.add(questoes("Treinar questões", "Geral", null, metaQuestoes));
                } else {
                    tipo = "estudo";
                    titulo = aula.topico();
                    foco = aula.materia();
                    topicosSemana.add(aula.topicoId());
                    tarefas.add(new Payloads.Tarefa("aula", aula.aulaId(), aula.titulo(), aula.topico(),
                            aula.topicoSlug(), aula.materia(), aula.materiaSlug(), aula.duracaoMin(), null));
                    tarefas.add(questoes("Questões de " + aula.materia(), aula.materia(),
                            aula.materiaSlug(), questoesPorDia));
                    if (data.getDayOfWeek() == DayOfWeek.SATURDAY && fase == 2) {
                        tipo = "simulado";
                        tarefas.add(questoes("Simulado cronometrado", "Geral", null, 20));
                    }
                }
            }

            int minutos = 0;
            for (Payloads.Tarefa tarefa : tarefas) {
                minutos += minutosDe(tarefa);
            }
            minutos = Math.max(20, Math.min(240, minutos));
            dias.add(new Payloads.DiaPlano(data.toString(), Calendario.diaSemana(data), data.equals(hoje),
                    tipo, titulo, foco, minutos, tarefas));
        }

        LocalDate fim = hoje.plusDays(6);
        return new Payloads.Cronograma(hoje.toString(), fim.toString(), fase, bloqueio,
                observacao(fase), dias);
    }

    private static List<Fila> montarFilas(List<Payloads.AulaPlano> pendentes, int fase) {
        Map<String, Fila> porMateria = new LinkedHashMap<>();
        for (Payloads.AulaPlano aula : pendentes) {
            porMateria.computeIfAbsent(aula.materiaSlug(),
                    chave -> new Fila(aula.materia(), aula.materiaSlug(), aula.prioridade()))
                    .lista.add(aula);
        }
        List<Fila> filas = new ArrayList<>();
        for (Fila fila : porMateria.values()) {
            if (fase == 2) {
                fila.lista.sort(Comparator.comparingInt(a -> pesoDificuldade(a.dificuldade())));
            }
            filas.add(fila);
        }
        return filas;
    }

    private static Payloads.AulaPlano proxima(List<Fila> filas) {
        Fila escolhida = null;
        for (Fila fila : filas) {
            if (fila.lista.isEmpty()) {
                continue;
            }
            if (escolhida == null) {
                escolhida = fila;
                continue;
            }
            double diferenca = fila.necessidade() - escolhida.necessidade();
            boolean empate = Math.abs(diferenca) <= 1e-9;
            if (diferenca > 1e-9 || (empate && fila.prioridade < escolhida.prioridade)) {
                escolhida = fila;
            }
        }
        if (escolhida == null) {
            return null;
        }
        Payloads.AulaPlano aula = escolhida.lista.remove(0);
        escolhida.agendados++;
        return aula;
    }

    private static Payloads.Tarefa questoes(String titulo, String materia, String materiaSlug, int quantidade) {
        return new Payloads.Tarefa("questoes", null, titulo, null, null, materia, materiaSlug, null, quantidade);
    }

    private static int minutosDe(Payloads.Tarefa tarefa) {
        if (tarefa.duracaoMin() != null) {
            return tarefa.duracaoMin();
        }
        if (tarefa.questoes() != null) {
            return Math.max(10, tarefa.questoes() * 2);
        }
        return 15;
    }

    private static int pesoDificuldade(String dificuldade) {
        return switch (dificuldade == null ? "medio" : dificuldade) {
            case "dificil" -> 0;
            case "medio" -> 1;
            default -> 2;
        };
    }

    private static String observacao(int fase) {
        if (fase == 3) {
            return "Reta final: conteúdo novo bloqueado. O plano prioriza revisão de temas já estudados, flashcards e erros anteriores.";
        }
        if (fase == 2) {
            return "Aprofundamento: temas médios e difíceis, simulado cronometrado no sábado e revisão no domingo.";
        }
        return "Base: prioridade para História, Geografia e Redação, intercalando Matemática, Linguagens e Ciências da Natureza.";
    }

    private static final class Fila {
        private final String materia;
        private final String slug;
        private final int prioridade;
        private final List<Payloads.AulaPlano> lista = new ArrayList<>();
        private int agendados;

        private Fila(String materia, String slug, int prioridade) {
            this.materia = materia;
            this.slug = slug;
            this.prioridade = prioridade;
        }

        private double peso() {
            if (prioridade <= 1) {
                return 3.0;
            }
            return prioridade == 2 ? 2.0 : 1.0;
        }

        private double necessidade() {
            return peso() / (agendados + 1);
        }
    }
}
