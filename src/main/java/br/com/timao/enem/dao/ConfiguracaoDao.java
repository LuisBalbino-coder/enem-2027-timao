package br.com.timao.enem.dao;

import br.com.timao.enem.db.Db;
import br.com.timao.enem.model.Payloads;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class ConfiguracaoDao {

    private ConfiguracaoDao() {
    }

    public static Payloads.Configuracao ler() {
        return Db.comConexao(c -> {
            String data = null;
            try (PreparedStatement ps = c.prepareStatement("SELECT valor FROM configuracao WHERE chave = 'data_alvo'");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    data = rs.getString(1);
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT nome, meta_diaria_minutos, meta_questoes_dia FROM usuario WHERE id = 1");
                 ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("Usuário local não inicializado");
                }
                return new Payloads.Configuracao(rs.getString(1), data == null ? "2027-11-07" : data,
                        rs.getInt(2), rs.getInt(3));
            }
        });
    }

    public static void salvar(String dataAlvo, Integer minutos, Integer questoes) {
        Db.comConexao(c -> {
            boolean autoCommit = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement(
                        "MERGE INTO configuracao (chave, valor) KEY (chave) VALUES (?, ?)")) {
                    ps.setString(1, "data_alvo");
                    ps.setString(2, dataAlvo);
                    ps.executeUpdate();
                }
                if (minutos != null) {
                    try (PreparedStatement ps = c.prepareStatement(
                            "UPDATE usuario SET meta_diaria_minutos = ? WHERE id = 1")) {
                        ps.setInt(1, minutos);
                        ps.executeUpdate();
                    }
                }
                if (questoes != null) {
                    try (PreparedStatement ps = c.prepareStatement(
                            "UPDATE usuario SET meta_questoes_dia = ? WHERE id = 1")) {
                        ps.setInt(1, questoes);
                        ps.executeUpdate();
                    }
                }
                c.commit();
                return Boolean.TRUE;
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(autoCommit);
            }
        });
    }
}
