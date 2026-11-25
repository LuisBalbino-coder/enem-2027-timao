package br.com.timao.enem.model;

import java.util.List;

/** Formatos de resposta da API (serializados como JSON camelCase). */
public final class Payloads {

    private Payloads() {
    }

    public record Materia(int id, String slug, String nome, String area, String cor, int prioridade,
                          String descricao, int topicos, int aulas, int percentual) {
    }

    public record Topico(int id, String slug, String titulo, String resumo, String dificuldade, int fase, int ordem,
                         String materiaSlug, String materiaNome, String materiaCor,
                         int aulas, int questoes, int flashcards, int aulasConcluidas) {
    }

    public record Aula(int id, String titulo, String tipo, String conteudo, String videoUrl, String videoArquivo,
                       Integer duracaoMin, int ordem, boolean concluida) {
    }

    public record Alternativa(String letra, String texto) {
    }

    public record Questao(int id, String codigo, String banca, Integer ano, String fonte, boolean autoral,
                          String dificuldade, String enunciado, String gabarito, String comentario,
                          List<Alternativa> alternativas, String materia, String topico) {
    }

    public record Flashcard(int id, String codigo, String frente, String verso, String dica,
                            String materia, String topico) {
    }

    public record TopicoDetalhe(Topico topico, List<Aula> aulas, List<Flashcard> flashcards) {
    }

    public record Repertorio(int id, String codigo, String area, String titulo, String autor, String citacao,
                             String aplicacao, String tags) {
    }

    public record TemaRedacao(int id, String codigo, String titulo, Integer ano, String proposta,
                              String textosMotivadores) {
    }

    public record Competencia(int numero, String titulo, String descricao, String criterios) {
    }

    public record Configuracao(String nome, String dataAlvo, int metaDiariaMinutos, int metaQuestoesDia) {
    }

    public record ConfigInput(String dataAlvo, Integer metaDiariaMinutos, Integer metaQuestoesDia) {
    }

    public record Contadores(int materias, int topicos, int aulas, int questoes, int flashcards, int repertorios) {
    }

    public record MetaHoje(int minutosHoje, int minutosMeta, int questoesHoje, int questoesMeta) {
    }

    public record Fase(int numero, String nome, String descricao, String ate) {
    }

    public record ResumoAulas(int concluidas, int total) {
    }

    public record Dashboard(String hoje, String dataAlvo, long diasRestantes, int urgencia, Fase fase,
                            MetaHoje meta, Contadores contadores, ResumoAulas resumo,
                            List<Materia> materias, List<Topico> proximos) {
    }

    public record Conclusao(int aulaId, boolean concluida, int aulasConcluidasTopico) {
    }

    public record SessaoInput(Integer minutos, String tipo, String descricao) {
    }

    public record Sessao(String data, int minutos, int totalHoje) {
    }

    public record Marco(String codigo, String data, String titulo, String descricao, String tipo) {
    }

    public record DiaCalendario(String data, int dia, boolean hoje, boolean alvo, int fase,
                                List<Marco> marcos) {
    }

    public record Calendario(String mes, String titulo, String hoje, String dataAlvo, int faseAtual,
                             int vazios, List<DiaCalendario> dias, List<Marco> marcos) {
    }

    public record AulaPlano(int aulaId, int topicoId, String topico, String topicoSlug, String materia,
                            String materiaSlug, int prioridade, int faseTopico, String dificuldade,
                            String titulo, Integer duracaoMin, boolean concluida) {
    }

    public record Tarefa(String tipo, Integer aulaId, String titulo, String topico, String topicoSlug,
                         String materia, String materiaSlug, Integer duracaoMin, Integer questoes) {
    }

    public record DiaPlano(String data, String diaSemana, boolean hoje, String tipo, String titulo,
                           String foco, int minutos, List<Tarefa> tarefas) {
    }

    public record Cronograma(String inicio, String fim, int fase, boolean bloqueioNovo, String observacao,
                             List<DiaPlano> dias) {
    }
}
