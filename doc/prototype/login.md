Excelente. Vamos projetar uma **Tela de Login (UI-02)** que seja segura, profissional e transmita confiança aos usuários corporativos, mantendo a identidade visual que definimos (Azul Profundo, Cinza Estrutural e Dourado/Laranja).

Para uma plataforma *Enterprise*, a prioridade é a segurança e a integração com os sistemas de identidade da empresa (SSO).

Aqui está o blueprint para o design da tela de login:

---

## Blueprint da Tela de Login (UI-02)

### 1. Layout Geral: Duas Colunas

Adotaremos um layout de tela dividida para equilibrar o *branding* e o formulário de acesso.

* **Lado Esquerdo (Painel de Branding e Contexto):**
* **Fundo:** Sólido na cor Azul Profundo corporativo.
* **Conteúdo:**
* **Logo AQuA-QE:** Uma versão grande e nítida do hexágono 3D (conforme `image_0.png`), centralizada vertical e horizontalmente.
* **Tagline (Opcional):** Abaixo do logo, uma frase curta em fonte branca. *Exemplo:* "Seu Centro de Comando de Agilidade Corporativa."
* **Elemento Visual Sutil:** Uma leve marca d'água ou um padrão geométrico abstrato, derivado das linhas do logo, para dar profundidade ao fundo azul.




* **Lado Lado Direito (Formulário de Acesso):**
* **Fundo:** Branco limpo (`#FFFFFF`) ou Cinza Gelo muito claro (`#F8FAFC`).
* **Conteúdo (Alinhado ao centro deste painel):**
* **Cabeçalho:** "Entrar na Plataforma" (em fonte grande, cor Azul Profundo).
* **Subtítulo:** "Use suas credenciais corporativas para acessar o AQuA-QE."





### 2. Métodos de Autenticação

Priorizaremos a experiência corporativa, que geralmente utiliza Single Sign-On (SSO).

* **Botão de SSO (Primário):**
* **Estilo:** Botão grande e proeminente. Fundo na cor Azul Profundo ou Cinza neutro, com borda sutil.
* **Ícone:** O ícone do Google G Suite (se aplicável ao contexto) ou um ícone genérico de chave/cadeado representando o SSO.
* **Texto:** **"Entrar com SSO Corporativo"** ou **"Entrar com Google Workspace"**.


* **Separador:**
* Uma linha horizontal fina com o texto "ou" em cinza claro no meio.



### 3. Formulário de Credenciais (Manual)

Para usuários convidados ou administradores que não utilizam SSO.

* **Campo de E-mail / Usuário:**
* Rótulo: "E-mail Corporativo" ou "Nome de Usuário".
* Placeholder: *ex: nome.sobrenome@empresa.com*.
* Ícone: Ícone de envelope (`✉️`) à esquerda.


* **Campo de Senha:**
* Rótulo: "Senha".
* Placeholder: *••••••••*.
* Ícone: Ícone de cadeado (`🔒`) à esquerda.
* Ação: Link "Esqueceu sua senha?" alinhado à direita, abaixo do campo.



### 4. Ações e Rodapé do Formulário

* **Checkbox "Lembrar-me":** Alinhado à esquerda.
* **Botão de Login Principal (CTA):**
* **Estilo:** Botão retangular de preenchimento total. Cor Dourada/Laranja vibrante do logo (`#F59E0B`).
* **Texto:** **"Entrar"** (em fonte branca ou azul escuro para contraste).


* **Link de Acesso Secundário:** Abaixo do botão principal.
* Texto: "Não tem uma conta? Solicite acesso."



### 5. Rodapé da Página (Minimalista)

* Localizado na parte inferior do painel direito.
* Texto menor em cor cinza: "Protegido por AQuA-QE Security Framework. Em conformidade com SOC 2."
* Links curtos: "Termos de Serviço" | "Política de Privacidade" | "Suporte".

---

Este design foca na eficiência para o usuário corporativo (priorizando o SSO) e na segurança visual, mantendo a elegância da marca AQuA-QE.

O que achou desta estrutura? Se estivermos alinhados, podemos prosseguir para a tela mais importante: o **Executive Overview / Home (UI-03)**.