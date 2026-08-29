# Noveris Staff Call — NeoForge 1.21.1

Sistema imersivo de convocação de jogadores pela staff.

## Comandos
### Chamados de jogadores
- `/novecall` — abre a tela para escolher RP ou OFF-RP, escrever um motivo de 10 a 120 caracteres e enviar.
- `/novecall atender <player>` — o primeiro OP que atender assume o chamado e é teleportado até o jogador com o efeito seguro.
- `/novecall recusar <player> <motivo>` — recusa um chamado pendente e informa a causa ao jogador.
- `/novecall pendentes` — lista os chamados ainda não atendidos.
- `/novecall concluir <player>` — conclui o atendimento assumido pela staff.
- `/novecall info <player>` — mostra tipo, motivo, status e staff responsável.
- `/novecall transferir <staff>` — transfere o atendimento atual para outro OP disponível.
- `/novecall reabrir <player>` — permite que a staff responsável reabra o atendimento recente.
- `/novecall retornar` — após concluir, devolve a staff ao local anterior.
- `/novecall cooldown consultar <player>` — consulta o tempo restante de cooldown.
- `/novecall cooldown remover <player>` — remove manualmente o cooldown de um jogador.
- `/novecall cancelar` — permite que o jogador cancele o próprio chamado pendente.
- `/novecall cancelar <player> <motivo>` — encerra à força um chamado ou atendimento ativo.

Somente jogadores OP recebem os alertas e podem executar os comandos administrativos. Cada jogador pode
ter apenas um chamado por vez. Chamados não respondidos expiram depois de cinco minutos e aplicam cooldown
de cinco minutos; quando uma staff aceita, o cooldown passa a ser de duas horas e permanece após reinícios. Chamados RP usam a
apresentação dourada e mística, enquanto chamados OFF-RP usam mensagens técnicas e a apresentação vermelha.
As mensagens usam cabeçalhos curtos em negrito, até quatro linhas, ícones simples e botões em uma linha
própria. O texto RP segue um tom medieval e cerimonial; o OFF-RP prioriza instruções técnicas e leitura rápida.
Uma staff só pode assumir um atendimento por vez. A chegada é confirmada após o teleporte seguro; se o
destino falhar, o jogador é avisado e o chamado volta à fila por cinco minutos. Atendimentos avisam aos
25 minutos e são encerrados aos 30, mantendo o retorno manual da staff.

### Convocação tradicional da staff
- `/novecall chamar <player>` — envia um pedido de convocação dourado.
- `/novecall chamar <player> <paleta>` — envia um pedido usando a paleta escolhida somente naquela chamada.
- `/novecall chamar <player> [paleta] forcar` — ignora a confirmação e inicia imediatamente.
- `/novecall aceitar` e `/novecall recusar` — respondem ao pedido pendente da staff.
- `/novecall status <player>` — consulta uma convocação tradicional.
- `/novecall retornar <player>` — devolve o jogador convocado ao local anterior.
- `/novecall historico <player>` — mostra os oito eventos administrativos mais recentes.
As paletas disponíveis aparecem ao pressionar Tab depois do jogador: `dourado`, `vermelho`, `azul`, `verde`, `roxo`, `branco`, `laranja`, `rosa`, `ciano` e `cinza`. A escolha muda em conjunto títulos, mensagens, partículas e barra de progresso apenas daquela convocação.

Por padrão, os comandos administrativos exigem permission level 2 (OP). Aceitar e recusar ficam disponíveis para todos.

Para testar a convocação tradicional sozinho, use `/novecall chamar <seu próprio jogador>`.

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
