# Noveris Staff Call — NeoForge 1.21.1

Sistema imersivo de convocação de jogadores pela staff.

## Comandos
- `/noveris chamar <player>` — inicia uma convocação de 4 segundos.
- `/noveris cancelar <player>` — cancela uma convocação ativa.
- `/noveris status <player>` — consulta se existe convocação ativa.

Os comandos exigem permission level 2 (OP).

Para testar sozinho, use `/noveris chamar <seu próprio jogador>`. A autochamada executa o fluxo completo, incluindo efeitos, bloqueio final e teleporte.

## Fluxo
O alvo recebe títulos e subtítulos dourados, mensagens de apoio, sons e partículas; nos 1,5 s finais sua posição é mantida pelo servidor. Ao final ele é teleportado para aproximadamente 2,5 blocos à frente da posição **atual** do staff.

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
