# Noveris Staff Call — NeoForge 1.21.1

Sistema imersivo de convocação de jogadores pela staff.

## Comandos
- `/noveris chamar <player>` — envia um pedido de convocação dourado.
- `/noveris chamar <player> <paleta>` — envia um pedido usando a paleta escolhida somente naquela chamada.
- `/noveris chamar <player> [paleta] forcar` — ignora a confirmação e inicia imediatamente.
- `/noveris aceitar` e `/noveris recusar` — respondem ao pedido pendente (também há botões clicáveis no chat).
- `/noveris cancelar <player>` — cancela uma convocação ativa.
- `/noveris status <player>` — consulta se existe convocação ativa.
- `/noveris retornar <player>` — devolve o jogador uma única vez ao local seguro anterior à convocação.
- `/noveris historico <player>` — mostra, no horário de Brasília, os oito eventos administrativos mais recentes do jogador.
As paletas disponíveis aparecem ao pressionar Tab depois do jogador: `dourado`, `vermelho`, `azul`, `verde`, `roxo`, `branco`, `laranja`, `rosa`, `ciano` e `cinza`. A escolha muda em conjunto títulos, mensagens, partículas e barra de progresso apenas daquela convocação.

Por padrão, os comandos administrativos exigem permission level 2 (OP). Aceitar e recusar ficam disponíveis para todos.

Para testar sozinho, use `/noveris chamar <seu próprio jogador>`. A autochamada executa o fluxo completo, incluindo efeitos, bloqueio final e teleporte.

## Fluxo
O alvo recebe títulos, mensagens, sons e partículas; nos 3 segundos finais ele é imobilizado e elevado suavemente em até 6 blocos, respeitando o espaço disponível acima. Ao final, é teleportado para aproximadamente 8 blocos à frente da posição **atual** do staff.

Antes da travessia, o mod procura uma posição segura próxima ao destino pretendido, com chão sólido, espaço livre e sem fluidos ou blocos perigosos. Se nenhum local seguro existir, o teleporte é cancelado.

Nos três segundos finais, um círculo de partículas aparece diante do invocador indicando a área prevista de chegada. Convocações, cancelamentos, falhas, conclusões e retornos são registrados de forma persistente no arquivo `noveris_staff_call_history.jsonl` dentro da pasta do mundo.

Falhas administrativas informam a causa específica, como jogador desconectado, dimensão indisponível, chão ausente ou perigoso, líquido, borda do mundo e espaço de chegada bloqueado.

## Configuração do servidor
Na primeira inicialização, o mod cria `serverconfig/noveris_staff_call-server.toml` dentro da pasta do mundo. O arquivo permite alterar duração, altura da levitação, distância de chegada, prazo de confirmação, fuso horário e permission level (0 a 4) de cada comando. Os valores são relidos ao iniciar cada chamado ou comando, sem necessidade de recompilar o JAR.

## Plataforma
- Minecraft 1.21.1
- NeoForge 21.1.248
- Java 21
- ModDevGradle 2.0.144

## Build normal
Linux/macOS:

```bash
./gradlew build
```

Windows:

```bat
gradlew.bat build
```

O JAR é gerado em `build/libs/`.

## Integração contínua

O workflow do GitHub Actions compila o projeto com Java 21 em cada push ou pull request e disponibiliza o JAR como artifact da execução.

Tags iniciadas por `v` (por exemplo, `v0.3.0`) também criam uma GitHub Release com o JAR permanente.
