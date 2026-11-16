package armazem.model;

/*
 Representa uma pessoa que pode entrar no sistema.

 Hierarquia: cada usuário tem um cargo (nível): Operador, Supervisor, Gerente ou ADM.
 O ADM é o maior e pode existir mais de um. Quem tem cargo maior manda em quem tem cargo menor.

 Código (ID) do usuário: sigla do cargo mais o número de entrada, por exemplo GER-0002.

 Permissões: o ADM pode tudo; os outros só fazem o que foi liberado nas 10 permissões.
 */
public class Usuario {

    private int id;                 // número de entrada, criado pelo banco (nunca se repete)
    private String login;           // o nome do usuário
    private String senhaHash;       // a senha NUNCA é guardada pura, só o "hash"
    private String salt;            // texto aleatório usado para proteger o hash
    private String codigo;          // o código (ID), por exemplo OPE-0003
    private int nivel;              // o cargo, veja a classe Nivel

    // as 10 permissões
    private boolean ver;            // ver a lista de produtos
    private boolean criar;          // criar produtos
    private boolean editar;         // editar produtos
    private boolean excluir;        // excluir produtos
    private boolean definirValor;   // colocar ou mudar o valor (preço) dos produtos
    private boolean adicionar;      // adicionar quantidade ao estoque
    private boolean retirar;        // retirar quantidade do estoque
    private boolean criarUsuario;   // criar novos usuários
    private boolean editarUsuario;  // editar usuários de cargo menor
    private boolean verHistorico;   // ver o histórico (quem fez o quê)

    // Cargo ADM é o maior: pode tudo
    public boolean isAdmin() { return nivel == Nivel.ADM; }

    // Métodos "pode...": o ADM sempre pode; os outros dependem da permissão.
    public boolean podeVer()           { return isAdmin() || ver; }
    public boolean podeCriar()         { return isAdmin() || criar; }
    public boolean podeEditar()        { return isAdmin() || editar; }
    public boolean podeExcluir()       { return isAdmin() || excluir; }
    public boolean podeDefinirValor()  { return isAdmin() || definirValor; }
    public boolean podeAdicionar()     { return isAdmin() || adicionar; }
    public boolean podeRetirar()       { return isAdmin() || retirar; }
    public boolean podeCriarUsuario()  { return isAdmin() || criarUsuario; }
    public boolean podeEditarUsuario() { return isAdmin() || editarUsuario; }
    public boolean podeVerHistorico()  { return isAdmin() || verHistorico; }

    // O maior cargo que este usuário pode dar a outra pessoa:
    // o ADM pode dar qualquer cargo; os outros só podem dar cargos MENORES que o seu.
    public int nivelMaximoQuePodeDar() {
        return isAdmin() ? Nivel.ADM : nivel - 1;
    }

    // Escreve as permissões em texto, por exemplo: ver produtos, criar produtos
    public String resumoPermissoes() {
        if (isAdmin()) {
            return "tudo (ADM)";
        }
        String r = "";
        if (ver) { r += "ver produtos, "; }
        if (criar) { r += "criar produtos, "; }
        if (editar) { r += "editar produtos, "; }
        if (excluir) { r += "excluir produtos, "; }
        if (definirValor) { r += "definir valor, "; }
        if (adicionar) { r += "adicionar ao estoque, "; }
        if (retirar) { r += "retirar do estoque, "; }
        if (criarUsuario) { r += "criar usuários, "; }
        if (editarUsuario) { r += "editar usuários, "; }
        if (verHistorico) { r += "ver histórico, "; }
        if (r.equals("")) {
            return "nenhuma";
        }
        return r.substring(0, r.length() - 2);   // tira a vírgula do final
    }

    // getters e setters: leem e mudam cada atributo
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getLogin() { return login; }
    public void setLogin(String login) { this.login = login; }
    public String getSenhaHash() { return senhaHash; }
    public void setSenhaHash(String senhaHash) { this.senhaHash = senhaHash; }
    public String getSalt() { return salt; }
    public void setSalt(String salt) { this.salt = salt; }
    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public int getNivel() { return nivel; }
    public void setNivel(int nivel) { this.nivel = nivel; }

    public void setVer(boolean ver) { this.ver = ver; }
    public void setCriar(boolean criar) { this.criar = criar; }
    public void setEditar(boolean editar) { this.editar = editar; }
    public void setExcluir(boolean excluir) { this.excluir = excluir; }
    public void setDefinirValor(boolean definirValor) { this.definirValor = definirValor; }
    public void setAdicionar(boolean adicionar) { this.adicionar = adicionar; }
    public void setRetirar(boolean retirar) { this.retirar = retirar; }
    public void setCriarUsuario(boolean criarUsuario) { this.criarUsuario = criarUsuario; }
    public void setEditarUsuario(boolean editarUsuario) { this.editarUsuario = editarUsuario; }
    public void setVerHistorico(boolean verHistorico) { this.verHistorico = verHistorico; }
}
