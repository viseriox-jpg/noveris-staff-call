# Noveris Staff Call — NeoForge 1.21.1

Sistema imersivo de convocação de jogadores pela staff.

## Comandos
- `/noveris chamar <player>` — inicia uma convocação dourada de 8 segundos.
- `/noveris chamar <player> <paleta>` — inicia uma convocação usando a paleta escolhida somente naquela chamada.
- `/noveris cancelar <player>` — cancela uma convocação ativa.
- `/noveris status <player>` — consulta se existe convocação ativa.
As paletas disponíveis aparecem ao pressionar Tab depois do jogador: `dourado`, `vermelho`, `azul`, `verde`, `roxo`, `branco`, `laranja`, `rosa`, `ciano` e `cinza`. A escolha muda em conjunto títulos, mensagens, partículas e barra de progresso apenas daquela convocação.

Os comandos exigem permission level 2 (OP).

Para testar sozinho, use `/noveris chamar <seu próprio jogador>`. A autochamada executa o fluxo completo, incluindo efeitos, bloqueio final e teleporte.

## Fluxo
O alvo recebe títulos e subtítulos dourados, mensagens de apoio, sons e partículas; nos 1,5 s finais sua posição é mantida pelo servidor. Ao final ele é teleportado para aproximadamente 8 blocos à frente da posição **atual** do staff.

Antes da travessia, o mod procura uma posição segura próxima ao destino pretendido, com chão sólido, espaço livre e sem fluidos ou blocos perigosos. Se nenhum local seguro existir, o teleporte é cancelado.

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
