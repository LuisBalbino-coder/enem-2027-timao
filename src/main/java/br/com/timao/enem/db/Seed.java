package br.com.timao.enem.db;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

/**
 * Carga idempotente dos conteúdos iniciais.
 * Chaves naturais (slug/codigo) permitem reiniciar o servidor sem duplicar nem perder progresso.
 */
public final class Seed {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> ARQUIVOS = List.of(
            "/seed/humanas.json",
            "/seed/redacao.json",
            "/seed/geral.json");

    private Seed() {
    }

    public static void carregar() {
        try (Connection conn = Db.conectar()) {
            conn.setAutoCommit(false);
            try {
                garantirUsuario(conn);
                garantirConfig(conn);
                for (String caminho : ARQUIVOS) {
                    JsonNode raiz = lerJson(caminho);
                    for (JsonNode materia : raiz.path("materias")) {
                        salvarMateria(conn, materia);
                    }
                    for (JsonNode repertorio : raiz.path("repertorios")) {
                        salvarRepertorio(conn, repertorio);
                    }
                    for (JsonNode tema : raiz.path("temas")) {
                        salvarTema(conn, tema);
                    }
                    for (JsonNode competencia : raiz.path("competencias")) {
                        salvarCompetencia(conn, competencia);
                    }
                }
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao carregar os seeds: " + e.getMessage(), e);
        }
    }

    // ---------------------------------------------------------------- base

    private static void garantirUsuario(Connection c) throws SQLException {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM usuario")) {
            rs.next();
            if (rs.getInt(1) > 0) {
                return;
            }
        }
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO usuario (id, nome, meta_diaria_minutos, meta_questoes_dia) VALUES (1, ?, ?, ?)")) {
            ps.setString(1, "Estudante");
            ps.setInt(2, 90);
            ps.setInt(3, 20);
            ps.executeUpdate();
        }
    }

    private static void garantirConfig(Connection c) throws SQLException {
        if (valorConfig(c, "data_alvo") == null) {
            gravarConfig(c, "data_alvo", "2027-11-07");
        }
    }

    // ------------------------------------------------------------ matérias

    private static void salvarMateria(Connection c, JsonNode m) throws SQLException {
        String slug = texto(m, "slug");
        if (slug == null) {
            throw new IllegalArgumentException("Matéria sem slug no seed");
        }
        Integer id = buscarId(c, "SELECT id FROM materia WHERE slug = ?", slug);
        if (id == null) {
            String sql = "INSERT INTO materia (slug, nome, area, cor, prioridade, descricao, ordem) VALUES (?,?,?,?,?,?,?)";
            try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, slug);
                ps.setString(2, texto(m, "nome", slug));
                ps.setString(3, texto(m, "area", "Geral"));
                ps.setString(4, texto(m, "cor", "#D90429"));
                ps.setInt(5, inteiro(m, "prioridade", 3));
                ps.setString(6, texto(m, "descricao"));
                ps.setInt(7, inteiro(m, "ordem", 99));
                ps.executeUpdate();
                try (ResultSet chaves = ps.getGeneratedKeys()) {
                    chaves.next();
                    id = chaves.getInt(1);
                }
            }
        } else {
            String sql = "UPDATE materia SET nome=?, area=?, cor=?, prioridade=?, descricao=?, ordem=? WHERE id=?";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, texto(m, "nome", slug));
                ps.setString(2, texto(m, "area", "Geral"));
                ps.setString(3, texto(m, "cor", "#D90429"));
                ps.setInt(4, inteiro(m, "prioridade", 3));
                ps.setString(5, texto(m, "descricao"));
                ps.setInt(6, inteiro(m, "ordem", 99));
                ps.setInt(7, id);
                ps.executeUpdate();
            }
        }

        int ordem = 1;
        for (JsonNode topico : m.path("topicos")) {
            salvarTopico(c, topico, id, ordem++);
        }
    }

    private static void salvarTopico(Connection c, JsonNode t, int materiaId, int ordem) throws SQLException {
        String slug = texto(t, "slug");
        if (slug == null) {
            throw new IllegalArgumentException("Tópico sem slug no seed");
        }
        Integer id = buscarId(c, "SELECT id FROM topico WHERE slug = ?", slug);
        if (id == null) {
            String sql = "INSERT INTO topico (materia_id, slug, titulo, resumo, dificuldade, fase, ordem) VALUES (?,?,?,?,?,?,?)";
            try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, materiaId);
                ps.setString(2, slug);
                ps.setString(3, texto(t, "titulo", slug));
                ps.setString(4, texto(t, "resumo"));
                ps.setString(5, texto(t, "dificuldade", "medio"));
                ps.setInt(6, inteiro(t, "fase", 1));
                ps.setInt(7, ordem);
                ps.executeUpdate();
                try (ResultSet chaves = ps.getGeneratedKeys()) {
                    chaves.next();
                    id = chaves.getInt(1);
                }
            }
        } else {
            String sql = "UPDATE topico SET materia_id=?, titulo=?, resumo=?, dificuldade=?, fase=?, ordem=? WHERE id=?";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, materiaId);
                ps.setString(2, texto(t, "titulo", slug));
                ps.setString(3, texto(t, "resumo"));
                ps.setString(4, texto(t, "dificuldade", "medio"));
                ps.setInt(5, inteiro(t, "fase", 1));
                ps.setInt(6, ordem);
                ps.setInt(7, id);
                ps.executeUpdate();
            }
        }

        int ordemAula = 1;
        for (JsonNode aula : t.path("aulas")) {
            salvarAula(c, aula, id, ordemAula++);
        }
        for (JsonNode flashcard : t.path("flashcards")) {
            salvarFlashcard(c, flashcard, id);
        }
        for (JsonNode questao : t.path("questoes")) {
            salvarQuestao(c, questao, id);
        }
    }

    private static void salvarAula(Connection c, JsonNode a, int topicoId, int ordem) throws SQLException {
        String titulo = texto(a, "titulo", "Aula " + ordem);
        String conteudo = texto(a, "conteudo");
        if (conteudo == null) {
            String arquivo = texto(a, "arquivo");
            if (arquivo != null) {
                conteudo = lerTexto("/seed/" + arquivo);
            }
        }
        String tipo = texto(a, "tipo", "leitura");
        String videoUrl = texto(a, "videoUrl");
        String videoArquivo = texto(a, "videoArquivo");
        Integer duracao = inteiroOuNull(a, "duracaoMin");

        Integer id = buscarId(c, "SELECT id FROM aula WHERE topico_id = ? AND ordem = ?", topicoId, ordem);
        if (id == null) {
            String sql = "INSERT INTO aula (topico_id, titulo, tipo, conteudo, video_url, video_arquivo, duracao_min, ordem) VALUES (?,?,?,?,?,?,?,?)";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, topicoId);
                ps.setString(2, titulo);
                ps.setString(3, tipo);
                ps.setString(4, conteudo);
                ps.setString(5, videoUrl);
                ps.setString(6, videoArquivo);
                setInteiro(ps, 7, duracao);
                ps.setInt(8, ordem);
                ps.executeUpdate();
            }
        } else {
            String sql = "UPDATE aula SET titulo=?, tipo=?, conteudo=?, video_url=?, video_arquivo=?, duracao_min=? WHERE id=?";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, titulo);
                ps.setString(2, tipo);
                ps.setString(3, conteudo);
                ps.setString(4, videoUrl);
                ps.setString(5, videoArquivo);
                setInteiro(ps, 6, duracao);
                ps.setInt(7, id);
                ps.executeUpdate();
            }
        }
    }

    private static void salvarFlashcard(Connection c, JsonNode f, int topicoId) throws SQLException {
        String codigo = texto(f, "codigo");
        if (codigo == null) {
            throw new IllegalArgumentException("Flashcard sem código no seed");
        }
        Integer id = buscarId(c, "SELECT id FROM flashcard WHERE codigo = ?", codigo);
        if (id == null) {
            String sql = "INSERT INTO flashcard (codigo, topico_id, frente, verso, dica) VALUES (?,?,?,?,?)";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, codigo);
                ps.setInt(2, topicoId);
                ps.setString(3, texto(f, "frente", codigo));
                ps.setString(4, texto(f, "verso", ""));
                ps.setString(5, texto(f, "dica"));
                ps.executeUpdate();
            }
        } else {
            String sql = "UPDATE flashcard SET topico_id=?, frente=?, verso=?, dica=? WHERE id=?";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, topicoId);
                ps.setString(2, texto(f, "frente", codigo));
                ps.setString(3, texto(f, "verso", ""));
                ps.setString(4, texto(f, "dica"));
                ps.setInt(5, id);
                ps.executeUpdate();
            }
        }
    }

    private static void salvarQuestao(Connection c, JsonNode q, int topicoId) throws SQLException {
        String codigo = texto(q, "codigo");
        if (codigo == null) {
            throw new IllegalArgumentException("Questão sem código no seed");
        }
        Integer id = buscarId(c, "SELECT id FROM questao WHERE codigo = ?", codigo);
        if (id == null) {
            String sql = "INSERT INTO questao (codigo, topico_id, banca, ano, fonte, autoral, dificuldade, enunciado, gabarito, comentario) VALUES (?,?,?,?,?,?,?,?,?,?)";
            try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                preencherQuestao(ps, q, codigo, topicoId);
                ps.executeUpdate();
                try (ResultSet chaves = ps.getGeneratedKeys()) {
                    chaves.next();
                    id = chaves.getInt(1);
                }
            }
        } else {
            String sql = "UPDATE questao SET topico_id=?, banca=?, ano=?, fonte=?, autoral=?, dificuldade=?, enunciado=?, gabarito=?, comentario=? WHERE id=?";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                preencherQuestaoUpdate(ps, q, topicoId);
                ps.setInt(10, id);
                ps.executeUpdate();
            }
        }

        try (PreparedStatement del = c.prepareStatement("DELETE FROM alternativa WHERE questao_id = ?")) {
            del.setInt(1, id);
            del.executeUpdate();
        }
        String sqlAlt = "INSERT INTO alternativa (questao_id, letra, texto) VALUES (?,?,?)";
        try (PreparedStatement ps = c.prepareStatement(sqlAlt)) {
            for (Map.Entry<String, JsonNode> campo : q.path("alternativas").properties()) {
                ps.setInt(1, id);
                ps.setString(2, campo.getKey());
                ps.setString(3, campo.getValue().asText());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static void preencherQuestao(PreparedStatement ps, JsonNode q, String codigo, int topicoId)
            throws SQLException {
        ps.setString(1, codigo);
        ps.setInt(2, topicoId);
        ps.setString(3, texto(q, "banca", "ENEM"));
        setInteiro(ps, 4, inteiroOuNull(q, "ano"));
        ps.setString(5, texto(q, "fonte"));
        ps.setBoolean(6, booleano(q, "autoral", true));
        ps.setString(7, texto(q, "dificuldade", "medio"));
        ps.setString(8, texto(q, "enunciado", ""));
        ps.setString(9, texto(q, "gabarito", "A"));
        ps.setString(10, texto(q, "comentario"));
    }

    private static void preencherQuestaoUpdate(PreparedStatement ps, JsonNode q, int topicoId) throws SQLException {
        ps.setInt(1, topicoId);
        ps.setString(2, texto(q, "banca", "ENEM"));
        setInteiro(ps, 3, inteiroOuNull(q, "ano"));
        ps.setString(4, texto(q, "fonte"));
        ps.setBoolean(5, booleano(q, "autoral", true));
        ps.setString(6, texto(q, "dificuldade", "medio"));
        ps.setString(7, texto(q, "enunciado", ""));
        ps.setString(8, texto(q, "gabarito", "A"));
        ps.setString(9, texto(q, "comentario"));
    }

    // ------------------------------------------------------------- redação

    private static void salvarRepertorio(Connection c, JsonNode r) throws SQLException {
        String codigo = texto(r, "codigo");
        if (codigo == null) {
            return;
        }
        Integer id = buscarId(c, "SELECT id FROM repertorio WHERE codigo = ?", codigo);
        if (id == null) {
            String sql = "INSERT INTO repertorio (codigo, area, titulo, autor, citacao, aplicacao, tags) VALUES (?,?,?,?,?,?,?)";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                preencherRepertorio(ps, r, codigo);
                ps.executeUpdate();
            }
        } else {
            String sql = "UPDATE repertorio SET area=?, titulo=?, autor=?, citacao=?, aplicacao=?, tags=? WHERE id=?";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, texto(r, "area", "Geral"));
                ps.setString(2, texto(r, "titulo", codigo));
                ps.setString(3, texto(r, "autor"));
                ps.setString(4, texto(r, "citacao", ""));
                ps.setString(5, texto(r, "aplicacao"));
                ps.setString(6, texto(r, "tags"));
                ps.setInt(7, id);
                ps.executeUpdate();
            }
        }
    }

    private static void preencherRepertorio(PreparedStatement ps, JsonNode r, String codigo) throws SQLException {
        ps.setString(1, codigo);
        ps.setString(2, texto(r, "area", "Geral"));
        ps.setString(3, texto(r, "titulo", codigo));
        ps.setString(4, texto(r, "autor"));
        ps.setString(5, texto(r, "citacao", ""));
        ps.setString(6, texto(r, "aplicacao"));
        ps.setString(7, texto(r, "tags"));
    }

    private static void salvarTema(Connection c, JsonNode t) throws SQLException {
        String codigo = texto(t, "codigo");
        if (codigo == null) {
            return;
        }
        Integer id = buscarId(c, "SELECT id FROM tema_redacao WHERE codigo = ?", codigo);
        if (id == null) {
            String sql = "INSERT INTO tema_redacao (codigo, titulo, ano, proposta, textos_motivadores) VALUES (?,?,?,?,?)";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, codigo);
                ps.setString(2, texto(t, "titulo", codigo));
                setInteiro(ps, 3, inteiroOuNull(t, "ano"));
                ps.setString(4, texto(t, "proposta"));
                ps.setString(5, texto(t, "textosMotivadores"));
                ps.executeUpdate();
            }
        } else {
            String sql = "UPDATE tema_redacao SET titulo=?, ano=?, proposta=?, textos_motivadores=? WHERE id=?";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, texto(t, "titulo", codigo));
                setInteiro(ps, 2, inteiroOuNull(t, "ano"));
                ps.setString(3, texto(t, "proposta"));
                ps.setString(4, texto(t, "textosMotivadores"));
                ps.setInt(5, id);
                ps.executeUpdate();
            }
        }
    }

    private static void salvarCompetencia(Connection c, JsonNode k) throws SQLException {
        int numero = inteiro(k, "numero", 0);
        Integer id = buscarId(c, "SELECT id FROM competencia_redacao WHERE numero = ?", numero);
        if (id == null) {
            String sql = "INSERT INTO competencia_redacao (numero, titulo, descricao, criterios) VALUES (?,?,?,?)";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, numero);
                ps.setString(2, texto(k, "titulo", "Competência " + numero));
                ps.setString(3, texto(k, "descricao"));
                ps.setString(4, texto(k, "criterios"));
                ps.executeUpdate();
            }
        } else {
            String sql = "UPDATE competencia_redacao SET titulo=?, descricao=?, criterios=? WHERE id=?";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, texto(k, "titulo", "Competência " + numero));
                ps.setString(2, texto(k, "descricao"));
                ps.setString(3, texto(k, "criterios"));
                ps.setInt(4, id);
                ps.executeUpdate();
            }
        }
    }

    // ------------------------------------------------------------ utilidades

    private static JsonNode lerJson(String caminho) {
        try (InputStream in = Seed.class.getResourceAsStream(caminho)) {
            if (in == null) {
                throw new IllegalStateException("Seed não encontrado: " + caminho);
            }
            return JSON.readTree(in);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao ler o seed " + caminho, e);
        }
    }

    private static String lerTexto(String caminho) {
        try (InputStream in = Seed.class.getResourceAsStream(caminho)) {
            if (in == null) {
                throw new IllegalStateException("Arquivo de aula não encontrado: " + caminho);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao ler a aula " + caminho, e);
        }
    }

    private static Integer buscarId(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : null;
            }
        }
    }

    private static String valorConfig(Connection c, String chave) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT valor FROM configuracao WHERE chave = ?")) {
            ps.setString(1, chave);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private static void gravarConfig(Connection c, String chave, String valor) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "MERGE INTO configuracao (chave, valor) KEY (chave) VALUES (?, ?)")) {
            ps.setString(1, chave);
            ps.setString(2, valor);
            ps.executeUpdate();
        }
    }

    private static void setInteiro(PreparedStatement ps, int indice, Integer valor) throws SQLException {
        if (valor == null) {
            ps.setNull(indice, java.sql.Types.INTEGER);
        } else {
            ps.setInt(indice, valor);
        }
    }

    private static String texto(JsonNode node, String campo) {
        JsonNode valor = node.get(campo);
        return valor == null || valor.isNull() ? null : valor.asText();
    }

    private static String texto(JsonNode node, String campo, String padrao) {
        String valor = texto(node, campo);
        return valor == null || valor.isBlank() ? padrao : valor;
    }

    private static int inteiro(JsonNode node, String campo, int padrao) {
        JsonNode valor = node.get(campo);
        return valor == null || valor.isNull() ? padrao : valor.asInt(padrao);
    }

    private static Integer inteiroOuNull(JsonNode node, String campo) {
        JsonNode valor = node.get(campo);
        return valor == null || valor.isNull() ? null : valor.asInt();
    }

    private static boolean booleano(JsonNode node, String campo, boolean padrao) {
        JsonNode valor = node.get(campo);
        return valor == null || valor.isNull() ? padrao : valor.asBoolean(padrao);
    }
}
