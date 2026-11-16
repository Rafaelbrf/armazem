package armazem.ddsql;

import armazem.model.Produto;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/*
 DDSQL = "Dados do SQL": a classe que conversa com o banco de dados.
 Todo o SQL dos produtos fica aqui (inserir, buscar, atualizar, apagar).

 COMO FUNCIONA CADA COMANDO SQL AQUI (leia uma vez, vale para todas as classes DDSQL):

   Passo 1: escrevemos o SQL com um ponto de interrogação em cada lugar onde
   entra um valor. Veja o método criar, mais abaixo: o SQL dele tem 4 interrogações,
   uma para cada coluna entre parênteses. A primeira é o nome, a segunda é o tipo,
   a terceira é a quantidade e a quarta é o valor em centavos.

   Passo 2: preenchemos cada interrogação com um valor, dizendo o número dela:
        ps.setString(1, nome);       preenche a 1ª interrogação com o texto nome
        ps.setString(2, tipo);       preenche a 2ª interrogação com o texto tipo
        ps.setInt(3, quantidade);    preenche a 3ª interrogação com o número quantidade
        ps.setLong(4, valor);        preenche a 4ª interrogação com o valor (número grande)
   (setString é para textos e setInt é para números inteiros)

   Passo 3: ps.executeUpdate() manda executar (usado quando MUDA algo: INSERT, UPDATE, DELETE).
   ps.executeQuery() manda executar e devolve o resultado (usado no SELECT).

   Passo 4: c.close() fecha a conexão com o banco quando terminamos.

 O motivo de usar interrogação em vez de juntar o texto direto no SQL é evitar o ataque
 chamado SQL Injection: o banco trata o que vem na interrogação sempre como DADO,
 nunca como comando, mesmo que alguém digite um SQL malicioso no campo.

 Obs.: no IntelliJ aparecem etiquetas cinzas como "parameterIndex:" ou "sql:".
 Elas são só dicas visuais da tela e NÃO fazem parte do código.
 */
public class ProdutoDDSQL {

    // Transforma a linha atual do resultado em um objeto Produto
    private Produto lerLinha(ResultSet rs) throws Exception {
        return new Produto(rs.getInt("id"), rs.getString("nome"), rs.getString("tipo"),
                rs.getInt("quantidade"), rs.getLong("valor_centavos"));
    }

    // Lista os produtos. 'busca' filtra pelo nome; 'tipo' filtra pelo tipo (texto vazio significa todos).
    public List<Produto> listar(String busca, String tipo) throws Exception {
        Connection c = Conexao.produtos();
        PreparedStatement ps = c.prepareStatement(
                "SELECT * FROM produtos WHERE nome LIKE ? AND (? = '' OR tipo = ?) ORDER BY id");
        ps.setString(1, "%" + busca + "%");
        ps.setString(2, tipo);
        ps.setString(3, tipo);

        ResultSet rs = ps.executeQuery();
        List<Produto> lista = new ArrayList<>();
        while (rs.next()) {   // para cada linha do resultado, cria um objeto Produto
            lista.add(lerLinha(rs));
        }
        c.close();
        return lista;
    }

    // Busca um produto pelo id (devolve null se não existir)
    public Produto buscar(int id) throws Exception {
        Connection c = Conexao.produtos();
        PreparedStatement ps = c.prepareStatement("SELECT * FROM produtos WHERE id = ?");
        ps.setInt(1, id);

        ResultSet rs = ps.executeQuery();
        Produto p = null;
        if (rs.next()) {
            p = lerLinha(rs);
        }
        c.close();
        return p;
    }

    // Cadastra um produto novo (o id o banco cria sozinho) e DEVOLVE esse id
    public int criar(String nome, String tipo, int quantidade, long valor) throws Exception {
        Connection c = Conexao.produtos();
        PreparedStatement ps = c.prepareStatement(
                "INSERT INTO produtos (nome, tipo, quantidade, valor_centavos) VALUES (?, ?, ?, ?)");
        ps.setString(1, nome);          // 1ª interrogação recebe o nome
        ps.setString(2, tipo);          // 2ª interrogação recebe o tipo
        ps.setInt(3, quantidade);       // 3ª interrogação recebe a quantidade (número)
        ps.setLong(4, valor);           // 4ª interrogação recebe o valor em centavos
        ps.executeUpdate();             // executa o INSERT: grava o produto no banco

        // pergunta ao banco qual foi o id que acabou de ser criado
        ResultSet rs = c.createStatement().executeQuery("SELECT last_insert_rowid()");
        int id = rs.getInt(1);
        c.close();
        return id;
    }

    // Quantos produtos existem (usado para colocar os exemplos na 1ª vez)
    public int contar() throws Exception {
        Connection c = Conexao.produtos();
        ResultSet rs = c.prepareStatement("SELECT COUNT(*) FROM produtos").executeQuery();
        int total = rs.getInt(1);
        c.close();
        return total;
    }

    // Altera os dados de um produto que já existe
    public void atualizar(int id, String nome, String tipo, int quantidade, long valor) throws Exception {
        Connection c = Conexao.produtos();
        PreparedStatement ps = c.prepareStatement(
                "UPDATE produtos SET nome = ?, tipo = ?, quantidade = ?, valor_centavos = ? WHERE id = ?");
        ps.setString(1, nome);
        ps.setString(2, tipo);
        ps.setInt(3, quantidade);
        ps.setLong(4, valor);
        ps.setInt(5, id);
        ps.executeUpdate();
        c.close();
    }

    // Soma uma quantidade ao estoque do produto (entrada).
    // O próprio banco faz a conta (quantidade = quantidade + valor), assim não há risco de erro.
    public void somar(int id, int quantidade) throws Exception {
        Connection c = Conexao.produtos();
        PreparedStatement ps = c.prepareStatement(
                "UPDATE produtos SET quantidade = quantidade + ? WHERE id = ?");
        ps.setInt(1, quantidade);       // 1ª interrogação recebe quanto vai somar
        ps.setInt(2, id);               // 2ª interrogação recebe o id do produto
        ps.executeUpdate();
        c.close();
    }

    // Tira uma quantidade do estoque do produto (saída).
    // A condição "quantidade >= valor" impede o estoque de ficar negativo.
    // O executeUpdate devolve quantas linhas foram alteradas: 1 deu certo, 0 não tinha o suficiente.
    public boolean subtrair(int id, int quantidade) throws Exception {
        Connection c = Conexao.produtos();
        PreparedStatement ps = c.prepareStatement(
                "UPDATE produtos SET quantidade = quantidade - ? WHERE id = ? AND quantidade >= ?");
        ps.setInt(1, quantidade);       // 1ª interrogação recebe quanto vai tirar
        ps.setInt(2, id);               // 2ª interrogação recebe o id do produto
        ps.setInt(3, quantidade);       // 3ª interrogação recebe a quantidade mínima exigida
        int linhasAlteradas = ps.executeUpdate();
        c.close();
        return linhasAlteradas == 1;
    }

    // Apaga um produto
    public void excluir(int id) throws Exception {
        Connection c = Conexao.produtos();
        PreparedStatement ps = c.prepareStatement("DELETE FROM produtos WHERE id = ?");
        ps.setInt(1, id);
        ps.executeUpdate();
        c.close();
    }

    // Lista os tipos que já existem (usado nas sugestões e no filtro)
    public List<String> listarTipos() throws Exception {
        Connection c = Conexao.produtos();
        PreparedStatement ps = c.prepareStatement("SELECT DISTINCT tipo FROM produtos ORDER BY tipo");
        ResultSet rs = ps.executeQuery();
        List<String> tipos = new ArrayList<>();
        while (rs.next()) {
            tipos.add(rs.getString("tipo"));
        }
        c.close();
        return tipos;
    }
}
