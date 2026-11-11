package com.zmove.jarvis

/**
 * Motor de detecção da palavra de ativação. Implementações podem usar o
 * Porcupine (local, baixo consumo) ou o reconhecedor do sistema.
 */
interface WakeWordEngine {

    /** Começa (ou retoma) a escuta. */
    fun start()

    /** Pausa a escuta sem liberar os recursos. */
    fun pause()

    /** Libera todos os recursos. */
    fun release()
}
