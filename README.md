# Jarvis

Assistente de voz para Android em português brasileiro. O app ouve comandos de
voz, interpreta frases simples e executa ações no dispositivo — abrir aplicativos,
pesquisar na web, definir timers e informar as horas. Também pode ficar ouvindo
em tempo integral, aguardando a palavra de ativação **“Jarvis”**.

## Funcionalidades

- **Comando por voz** — toque em “Ouvir comando” e fale em pt-BR.
- **Abrir aplicativos** — WhatsApp, Spotify, Chrome, YouTube e Instagram
  (com fallback para a Play Store quando o app não está instalado).
- **Pesquisa na web** — Google e YouTube.
- **Previsão do tempo** — abre a busca do Google para a consulta.
- **Timers** — “timer de 5 minutos”, “timer de 30 segundos”, “daqui a 2 horas”.
- **Horas** — “que horas são?”.
- **Escuta em tempo integral sem chave** — serviço em foreground aguardando
  “Jarvis” com o **sherpa-onnx** (KeywordSpotter). O áudio é processado no
  próprio aparelho, sem Access Key e sem enviar nada para a nuvem.
- **Motores alternativos** — Porcupine (offline, requer chave Picovoice) e o
  `SpeechRecognizer` do Google (online), selecionáveis no app.
- **Retomada automática** — reinicia a escuta após o boot do aparelho (Android 13
  ou anterior) e pausa/retoma o microfone enquanto um comando é capturado.

## Estrutura

```
app/src/main/java/com/zmove/jarvis/
├── MainActivity.kt            # UI, permissões, seleção de motor e um comando
├── JarvisService.kt           # serviço de escuta em foreground
├── WakeWordEngine.kt          # contrato dos motores de wake word
├── SherpaWakeWordEngine.kt    # wake word offline sem chave (sherpa-onnx)
├── PorcupineWakeWordEngine.kt # wake word local opcional (Picovoice)
├── SpeechWakeWordEngine.kt    # fallback com SpeechRecognizer
├── WakeWordPreferences.kt     # motor, Access Key e preferências
├── BootReceiver.kt            # retomada da escuta após o boot
├── CommandProcessor.kt        # interpretação das frases (puro Kotlin, testável)
├── Speech.kt                  # normalização de texto e intents de reconhecimento
├── TimerScheduler.kt          # agendamento de timers com AlarmManager
└── TimerReceiver.kt           # notificação quando o timer dispara
```

O `CommandProcessor` não depende do Android e é coberto por testes unitários.

## Escuta em tempo integral

1. Abra o app e conceda a permissão de microfone.
2. Ative **“Ouvir em tempo integral”**.
3. Diga **“Jarvis”**. O assistente abre, captura o comando e volta a ouvir.

O motor padrão é o **sherpa-onnx**, que roda 100% offline e não exige chave. Em
**“Motor de escuta”** é possível trocar para:

| Motor | Como funciona | Requer chave? |
| --- | --- | --- |
| **Sherpa** (padrão) | KeywordSpotter zipformer local, modelo em inglês de ~6 MB | Não |
| **Porcupine** | Detecção local da Picovoice | Sim, Access Key gratuita |
| **Google** | `SpeechRecognizer` do sistema | Não, mas usa rede |

Para maior estabilidade, use **“Ignorar otimização de bateria”** e mantenha a
notificação fixa ativa. O botão **“Desativar”** na notificação encerra a escuta
e desliga a opção.

### Como a keyword “JARVIS” é gerada

O modelo KWS usa tokens SentencePiece. A linha em
`app/src/main/assets/kws/keywords.txt` é:

```
▁JA R VI S @JARVIS
```

Ela foi gerada com o próprio `bpe.model` do modelo e validada contra os nove
exemplos oficiais (`HELLO WORLD`, `ALEXA`, `HEY SIRI`, etc.). Com o modelo
`encoder.int8` + `joiner.int8` + `decoder` fp32, a configuração padrão
(`keywordsScore = 1.0`, `keywordsThreshold = 0.25`) detectou **7/7** áudios
positivos em pt-BR e en-US — incluindo “Ei, Jarvis”, “Jarvis, que horas são?” e
fala lenta/rápida — e rejeitou **10/10** negativos, inclusive “já vi”, “já viu”,
“jararaca” e “Carlos”.

> O modelo é treinado em inglês. A detecção foi validada no aparelho
> (Motorola Edge 20 Pro) com voz real em pt-BR. O TTS pt-BR do Google **não** é
> um bom teste: a pronúncia sintetizada não é reconhecida pelo modelo, embora a
> mesma palavra falada por uma pessoa seja detectada. Ajuste
> `keywordsThreshold` em `SherpaWakeWordEngine.kt` se necessário (menor = mais
> sensível).

O laço de escuta segue o exemplo oficial do sherpa-onnx: `getResult()` é
consultado após cada `decode()`, dentro do `while (isReady())`, para que um
resultado não seja sobrescrito pelo chunk seguinte.

### Por que não Vosk?

O `vosk-model-small-pt-0.3` não contém “jarvis” no vocabulário e o modelo small
não permite adicionar palavras fora do vocabulário em runtime. O sherpa-onnx
resolve isso com keyword spotting de vocabulário aberto, sem retreinar o modelo.

## Requisitos

- Android Studio (Ladybug ou mais recente) ou JDK 17+.
- Android SDK 35 instalado.
- Dispositivo ou emulador com serviço de reconhecimento de voz (para os
  comandos; a wake word do Sherpa não depende dele).
- Conta Picovoice apenas se quiser usar o motor Porcupine.

## Como compilar

```bash
./gradlew assembleDebug
```

O APK é gerado em `app/build/outputs/apk/debug/app-debug.apk`.

O AAR oficial do sherpa-onnx fica versionado em
`app/libs/sherpa-onnx-2.0.1.aar` (release oficial v2.0.1, Apache 2.0). O modelo
KWS fica em `app/src/main/assets/kws/` e é copiado para o armazenamento interno
do app na primeira execução.

Para rodar os testes unitários:

```bash
./gradlew test
```

## Permissões

| Permissão | Uso |
| --- | --- |
| `RECORD_AUDIO` | Captura de voz e wake word |
| `POST_NOTIFICATIONS` | Notificação do serviço e avisos de timer |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_MICROPHONE` | Escuta em tempo integral |
| `RECEIVE_BOOT_COMPLETED` | Retomar a escuta após reiniciar o aparelho |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Evitar que o sistema mate o serviço |
| `INTERNET` | Reconhecimento de fala do sistema e pesquisas |

## Limitações conhecidas

- O modelo KWS do Sherpa é **em inglês**; “Jarvis” funciona bem, mas a
  sensibilidade pode variar com sotaque, ruído e distância do microfone.
- O reconhecimento dos comandos em si continua usando o serviço de fala do
  sistema (online). Apenas a palavra de ativação é 100% offline no modo Sherpa.
- No Android 14+ o sistema impede iniciar um serviço de microfone a partir do
  boot; nesse caso é preciso abrir o app após reiniciar o aparelho.
- Fabricantes com gerenciamento agressivo de bateria (Xiaomi, Samsung, etc.)
  podem encerrar o serviço mesmo com a isenção concedida.
- Não há integração com LLM ainda. Frases fora dos comandos mapeados retornam
  “ainda não sei fazer isso”.

## Roadmap

- [x] Wake word local com Picovoice Porcupine.
- [x] Wake word offline sem chave com sherpa-onnx (padrão).
- [ ] Transcrição de comandos offline (Vosk/sherpa-onnx ASR) para remover a
      dependência do serviço do Google.
- [ ] Integração com Gemini para comandos livres e respostas faladas (TTS).
- [ ] Mais comandos: alarmes, ligações, mensagens, controle de mídia.
- [ ] Testes instrumentados e CI (GitHub Actions).
- [ ] Internacionalização (pt-BR padrão, en como alternativa).

## Créditos e licenças

- [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) — Apache 2.0.
- Modelo `sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01` — Apache 2.0.
- [Picovoice Porcupine](https://picovoice.ai/) — opcional, sujeito à licença
  da Picovoice.

## Licença

Projeto privado da Zmove Digital. Todos os direitos reservados.
