package armazem.ddsql;

import armazem.model.Registro;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/*
 DDSQL do histórico (Dados do SQL): grava e lê o "quem fez o quê".
 O histórico só cresce: não existe método para apagar ou editar registros.
 */
public class HistoricoDDSQL {

    // Grava uma linha no histórico. A data e a hora são colocadas automaticamente.
    public void registrar(String usuario, String codigo, String acao, int produtoId, String detalhes) throws Exception {
        String agora = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));

        Connection c = Conexao.historico();
        // O SQL tem 6 interrogações (uma para cada coluna entre parênteses), na mesma ordem:
        // data_hora, usuario, usuario_codigo, acao, produto_id e detalhes
        PreparedStatement ps = c.prepareStatement(
                "INSERT INTO historico (data_hora, usuario, usuario_codigo, acao, produto_id, detalhes) "
                + "VALUES (?, ?, ?, ?, ?, ?)");

        // Agora preenchemos cada interrogação pelo número dela:
        ps.setString(1, agora);         // 1ª recebe a data e hora (texto)
        ps.setString(2, usuario);       // 2ª recebe o nome de quem fez (texto)
        ps.setString(3, codigo);        // 3ª recebe o código (ID) de quem fez (texto)
        ps.setString(4, acao);          // 4ª recebe o que fez (texto)
        ps.setInt(5, produtoId);        // 5ª recebe o ID do produto (número)
        ps.setString(6, detalhes);      // 6ª recebe os detalhes (texto)

        ps.executeUpdate();             // executa o INSERT: grava a linha no historico.db
        c.close();                      // fecha a conexão com o banco
    }

    // Lista os registros, do mais novo para o mais antigo (no máximo 300).
    // Se produtoId for -1, mostra tudo; senão, mostra só os registros daquele produto.
    public List<Registro> listar(int produtoId) throws Exception {
        Connection c = Conexao.historico();
        PreparedStatement ps = c.prepareStatement(
                "SELECT * FROM historico WHERE (? = -1 OR produto_id = ?) ORDER BY id DESC LIMIT 300");
        ps.setInt(1, produtoId);
        ps.setInt(2, produtoId);

        ResultSet rs = ps.executeQuery();
        List<Registro> lista = new ArrayList<>();
        while (rs.next()) {
            lista.add(new Registro(rs.getString("data_hora"), rs.getString("usuario"),
                    rs.getString("usuario_codigo"), rs.getString("acao"),
                    rs.getInt("produto_id"), rs.getString("detalhes")));
        }
        c.close();
        return lista;
    }
}
