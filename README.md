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
- **Escuta em tempo integral** — serviço em foreground aguardando “Jarvis”.
  Usa **Picovoice Porcupine** (wake word local e offline) quando há uma Access
  Key configurada; sem a chave, cai para o `SpeechRecognizer` do Google.
- **Retomada automática** — reinicia a escuta após o boot do aparelho (Android 13
  ou anterior) e pausa/retoma o microfone enquanto um comando é capturado.

## Estrutura

```
app/src/main/java/com/zmove/jarvis/
├── MainActivity.kt            # UI, permissões e captura de um comando
├── JarvisService.kt           # serviço de escuta em foreground
├── WakeWordEngine.kt          # contrato dos motores de wake word
├── PorcupineWakeWordEngine.kt # wake word local (Picovoice)
├── SpeechWakeWordEngine.kt    # fallback com SpeechRecognizer
├── WakeWordPreferences.kt     # Access Key, sensibilidade e preferências
├── BootReceiver.kt            # retomada da escuta após o boot
├── CommandProcessor.kt        # interpretação das frases (puro Kotlin, testável)
├── Speech.kt                  # normalização de texto e intents de reconhecimento
├── TimerScheduler.kt          # agendamento de timers com AlarmManager
└── TimerReceiver.kt           # notificação quando o timer dispara
```

O `CommandProcessor` não depende do Android e é coberto por testes unitários.

## Escuta em tempo integral

1. Crie uma conta gratuita em [console.picovoice.ai](https://console.picovoice.ai/)
   e copie sua **Access Key**.
2. No app, toque em **“Configurar chave Picovoice”** e cole a chave.
3. Ative **“Ouvir em tempo integral”**.

Com a chave configurada, o áudio é processado **no próprio aparelho** pelo
Porcupine (a chave é validada online na inicialização, mas nenhum áudio sai do
dispositivo). Ao detectar “Jarvis”, o serviço pausa a escuta, abre o assistente,
captura o comando e volta a ouvir.

Para maior estabilidade, use **“Ignorar otimização de bateria”** e mantenha a
notificação fixa ativa. O botão **“Desativar”** na notificação encerra a escuta
e desliga a opção.

> A Access Key fica salva nas preferências privadas do app (SharedPreferences).
> Não versione sua chave no repositório.

## Requisitos

- Android Studio (Ladybug ou mais recente) ou JDK 17+.
- Android SDK 35 instalado.
- Dispositivo ou emulador com Google app / serviço de reconhecimento de voz.
- Conta Picovoice (gratuita) para o modo local.

## Como compilar

```bash
./gradlew assembleDebug
```

O APK é gerado em `app/build/outputs/apk/debug/app-debug.apk`.

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
| `INTERNET` | Reconhecimento de fala, validação da chave e pesquisas |

## Limitações conhecidas

- Sem Access Key, a escuta contínua usa o `SpeechRecognizer` do Google, que
  depende de rede e consome mais bateria.
- No Android 14+ o sistema impede iniciar um serviço de microfone a partir do
  boot; nesse caso é preciso abrir o app após reiniciar o aparelho.
- Fabricantes com gerenciamento agressivo de bateria (Xiaomi, Samsung, etc.)
  podem encerrar o serviço mesmo com a isenção concedida.
- O reconhecimento dos comandos em si usa o serviço de fala do sistema; o
  Porcupine cuida apenas da palavra de ativação.
- Não há integração com LLM ainda. Frases fora dos comandos mapeados retornam
  “ainda não sei fazer isso”.

## Roadmap

- [x] Wake word local com Picovoice Porcupine.
- [ ] Integração com Gemini para comandos livres e respostas faladas (TTS).
- [ ] Mais comandos: alarmes, ligações, mensagens, controle de mídia.
- [ ] Testes instrumentados e CI (GitHub Actions).
- [ ] Internacionalização (pt-BR padrão, en como alternativa).

## Licença

Projeto privado da Zmove Digital. Todos os direitos reservados.
