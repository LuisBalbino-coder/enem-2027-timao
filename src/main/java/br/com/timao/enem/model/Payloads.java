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
                         int aulas, int questoes, int flashcards) {
    }

    public record Aula(int id, String titulo, String tipo, String conteudo, String videoUrl, String videoArquivo,
                       Integer duracaoMin, int ordem) {
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

    public record Dashboard(String hoje, String dataAlvo, long diasRestantes, int urgencia, Fase fase,
                            MetaHoje meta, Contadores contadores, List<Materia> materias,
                            List<Topico> proximos) {
    }
}
