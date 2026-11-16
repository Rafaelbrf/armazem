package armazem.ddsql;

import armazem.model.Nivel;
import armazem.model.Usuario;
import armazem.seguranca.Senha;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/*
 DDSQL dos usuários (Dados do SQL): todo o SQL do banco de logins fica aqui.
 No banco, "verdadeiro ou falso" é guardado como 1 ou 0.
 */
public class UsuarioDDSQL {

    // Transforma true ou false em 1 ou 0 para guardar no banco
    private int paraInt(boolean valor) {
        return valor ? 1 : 0;
    }

    // Transforma a linha atual do resultado em um objeto Usuario
    private Usuario lerLinha(ResultSet rs) throws Exception {
        Usuario u = new Usuario();
        u.setId(rs.getInt("id"));
        u.setLogin(rs.getString("login"));
        u.setSenhaHash(rs.getString("senha_hash"));
        u.setSalt(rs.getString("salt"));
        String codigo = rs.getString("codigo");
        u.setCodigo(codigo == null ? "" : codigo);
        u.setNivel(rs.getInt("nivel"));
        u.setVer(rs.getInt("pode_ver") == 1);
        u.setCriar(rs.getInt("pode_criar") == 1);
        u.setEditar(rs.getInt("pode_editar") == 1);
        u.setExcluir(rs.getInt("pode_excluir") == 1);
        u.setDefinirValor(rs.getInt("pode_definir_valor") == 1);
        u.setAdicionar(rs.getInt("pode_adicionar") == 1);
        u.setRetirar(rs.getInt("pode_retirar") == 1);
        u.setCriarUsuario(rs.getInt("pode_criar_usuario") == 1);
        u.setEditarUsuario(rs.getInt("pode_editar_usuario") == 1);
        u.setVerHistorico(rs.getInt("pode_ver_historico") == 1);
        return u;
    }

    // Quantos usuários existem (usado para saber se é a 1ª vez que o sistema roda)
    public int contar() throws Exception {
        Connection c = Conexao.usuarios();
        ResultSet rs = c.prepareStatement("SELECT COUNT(*) FROM usuarios").executeQuery();
        int total = rs.getInt(1);
        c.close();
        return total;
    }

    // Quantos usuários têm o cargo ADM (o sistema nunca pode ficar sem nenhum)
    public int contarAdmins() throws Exception {
        Connection c = Conexao.usuarios();
        PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM usuarios WHERE nivel = ?");
        ps.setInt(1, Nivel.ADM);
        int total = ps.executeQuery().getInt(1);
        c.close();
        return total;
    }

    /*
     Cria um usuário novo e devolve o código (ID) dele.
     A senha é guardada como hash, nunca pura.

     Como o código nasce: o banco numera o usuário sozinho (1, 2, 3...), e esse número de entrada
     nunca se repete. O código é a sigla do cargo mais esse número, por exemplo GER-0003.
     */
    public String criar(Usuario u, String senha) throws Exception {
        String salt = Senha.novoSalt();
        String hash = Senha.gerarHash(senha, salt);

        Connection c = Conexao.usuarios();
        PreparedStatement ps = c.prepareStatement(
                "INSERT INTO usuarios (login, senha_hash, salt, admin, nivel, pode_ver, pode_criar, "
                + "pode_editar, pode_excluir, pode_definir_valor, pode_adicionar, pode_retirar, "
                + "pode_criar_usuario, pode_editar_usuario, pode_ver_historico) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
        ps.setString(1, u.getLogin());
        ps.setString(2, hash);
        ps.setString(3, salt);
        ps.setInt(4, paraInt(u.isAdmin()));
        ps.setInt(5, u.getNivel());
        ps.setInt(6, paraInt(u.podeVer()));
        ps.setInt(7, paraInt(u.podeCriar()));
        ps.setInt(8, paraInt(u.podeEditar()));
        ps.setInt(9, paraInt(u.podeExcluir()));
        ps.setInt(10, paraInt(u.podeDefinirValor()));
        ps.setInt(11, paraInt(u.podeAdicionar()));
        ps.setInt(12, paraInt(u.podeRetirar()));
        ps.setInt(13, paraInt(u.podeCriarUsuario()));
        ps.setInt(14, paraInt(u.podeEditarUsuario()));
        ps.setInt(15, paraInt(u.podeVerHistorico()));
        ps.executeUpdate();

        // pergunta ao banco qual foi o número de entrada do usuário que acabou de ser criado
        int numero = c.createStatement().executeQuery("SELECT last_insert_rowid()").getInt(1);

        // monta o código e grava no usuário
        String codigo = Nivel.gerarCodigo(u.getNivel(), numero);
        PreparedStatement ps2 = c.prepareStatement("UPDATE usuarios SET codigo = ? WHERE id = ?");
        ps2.setString(1, codigo);
        ps2.setInt(2, numero);
        ps2.executeUpdate();
        c.close();
        return codigo;
    }

    // Busca pelo nome. Não diferencia maiúsculas de minúsculas (Ana e ana são o mesmo nome).
    public Usuario buscarPorLogin(String login) throws Exception {
        Connection c = Conexao.usuarios();
        PreparedStatement ps = c.prepareStatement("SELECT * FROM usuarios WHERE login = ? COLLATE NOCASE");
        ps.setString(1, login);
        ResultSet rs = ps.executeQuery();
        Usuario u = null;
        if (rs.next()) {
            u = lerLinha(rs);
        }
        c.close();
        return u;
    }

    public Usuario buscarPorId(int id) throws Exception {
        Connection c = Conexao.usuarios();
        PreparedStatement ps = c.prepareStatement("SELECT * FROM usuarios WHERE id = ?");
        ps.setInt(1, id);
        ResultSet rs = ps.executeQuery();
        Usuario u = null;
        if (rs.next()) {
            u = lerLinha(rs);
        }
        c.close();
        return u;
    }

    // Confere login e senha. Devolve o usuário se estiver certo, ou null se estiver errado.
    public Usuario autenticar(String login, String senha) throws Exception {
        Usuario u = buscarPorLogin(login);
        if (u == null) {
            return null;                        // login não existe
        }
        String hashDigitado = Senha.gerarHash(senha, u.getSalt());
        if (hashDigitado.equals(u.getSenhaHash())) {
            return u;                           // senha certa
        }
        return null;                            // senha errada
    }

    public List<Usuario> listar() throws Exception {
        Connection c = Conexao.usuarios();
        ResultSet rs = c.prepareStatement("SELECT * FROM usuarios ORDER BY id").executeQuery();
        List<Usuario> lista = new ArrayList<>();
        while (rs.next()) {
            lista.add(lerLinha(rs));
        }
        c.close();
        return lista;
    }

    /*
     Salva as mudanças de um usuário: nome, cargo e permissões (a senha tem método próprio).
     O código é montado de novo, porque a sigla acompanha o cargo. O número de entrada
     continua o mesmo, então o código continua único.
     */
    public void atualizar(Usuario u) throws Exception {
        u.setCodigo(Nivel.gerarCodigo(u.getNivel(), u.getId()));

        Connection c = Conexao.usuarios();
        PreparedStatement ps = c.prepareStatement(
                "UPDATE usuarios SET login = ?, admin = ?, nivel = ?, codigo = ?, pode_ver = ?, "
                + "pode_criar = ?, pode_editar = ?, pode_excluir = ?, pode_definir_valor = ?, "
                + "pode_adicionar = ?, pode_retirar = ?, pode_criar_usuario = ?, "
                + "pode_editar_usuario = ?, pode_ver_historico = ? WHERE id = ?");
        ps.setString(1, u.getLogin());
        ps.setInt(2, paraInt(u.isAdmin()));
        ps.setInt(3, u.getNivel());
        ps.setString(4, u.getCodigo());
        ps.setInt(5, paraInt(u.podeVer()));
        ps.setInt(6, paraInt(u.podeCriar()));
        ps.setInt(7, paraInt(u.podeEditar()));
        ps.setInt(8, paraInt(u.podeExcluir()));
        ps.setInt(9, paraInt(u.podeDefinirValor()));
        ps.setInt(10, paraInt(u.podeAdicionar()));
        ps.setInt(11, paraInt(u.podeRetirar()));
        ps.setInt(12, paraInt(u.podeCriarUsuario()));
        ps.setInt(13, paraInt(u.podeEditarUsuario()));
        ps.setInt(14, paraInt(u.podeVerHistorico()));
        ps.setInt(15, u.getId());
        ps.executeUpdate();
        c.close();
    }

    public void trocarSenha(int id, String novaSenha) throws Exception {
        String salt = Senha.novoSalt();
        String hash = Senha.gerarHash(novaSenha, salt);

        Connection c = Conexao.usuarios();
        PreparedStatement ps = c.prepareStatement(
                "UPDATE usuarios SET senha_hash = ?, salt = ? WHERE id = ?");
        ps.setString(1, hash);
        ps.setString(2, salt);
        ps.setInt(3, id);
        ps.executeUpdate();
        c.close();
    }

    public void excluir(int id) throws Exception {
        Connection c = Conexao.usuarios();
        PreparedStatement ps = c.prepareStatement("DELETE FROM usuarios WHERE id = ?");
        ps.setInt(1, id);
        ps.executeUpdate();
        c.close();
    }

    /*
     Para bancos de versões antigas: quem ainda não tem código ganha um agora.
     Roda toda vez que o programa inicia, mas só mexe em quem está sem código.
     */
    public void gerarCodigosFaltando() throws Exception {
        Connection c = Conexao.usuarios();
        ResultSet rs = c.prepareStatement(
                "SELECT id, nivel FROM usuarios WHERE codigo IS NULL OR codigo = ''").executeQuery();

        // primeiro guarda todos numa lista, depois atualiza um por um
        List<int[]> faltando = new ArrayList<>();
        while (rs.next()) {
            faltando.add(new int[] {rs.getInt("id"), rs.getInt("nivel")});
        }
        for (int[] f : faltando) {
            PreparedStatement ps = c.prepareStatement("UPDATE usuarios SET codigo = ? WHERE id = ?");
            ps.setString(1, Nivel.gerarCodigo(f[1], f[0]));
            ps.setInt(2, f[0]);
            ps.executeUpdate();
        }
        c.close();
    }
}
