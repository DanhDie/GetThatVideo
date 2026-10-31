"""
GetThatVideo - Backend API (Baseado em https://github.com/zararashraf/youtube-video-downloader-api)
===================================================================================================

Este servidor Flask amplia o repositório original `zararashraf/youtube-video-downloader-api`
para suportar todas as funcionalidades do aplicativo Kotlin Multiplatform:
1. Validação ampla de URLs do YouTube (watch, youtu.be, shorts, music).
2. Extração de informações completas do vídeo + listagem de qualidades de Vídeo (MP4) e Áudio (MP3).
3. Download em MP4 (com suporte a streams progressivos e adaptativos via FFmpeg) e MP3 (conversão de áudio).
4. Seleção customizada da pasta de destino (output_dir) e streaming direto para clientes móveis.

Endpoints disponíveis:
- GET  /health                  -> Verifica se a API e o FFmpeg estão ativos.
- POST /video_info              -> Retorna metadados do vídeo e qualidades disponíveis (MP4 e MP3).
- POST /available_resolutions   -> Retorna resoluções de vídeo e bitrates de áudio disponíveis.
- POST /download/<resolution>   -> Endpoint original do repositório (compatibilidade).
- POST /download                -> Endpoint completo (url, format [mp4|mp3], quality, output_dir).
- POST /download_stream         -> Faz o download e retorna o arquivo binário via HTTP (para Android/remoto).
"""

from flask import Flask, request, jsonify, send_file
from pytubefix import YouTube
import os
import re
import shutil
import subprocess
import tempfile
import uuid

app = Flask(__name__)


# ==============================================================================
# FUNÇÕES UTILITÁRIAS
# ==============================================================================

def is_valid_youtube_url(url: str) -> bool:
    """
    Valida se a URL informada pertence ao YouTube.
    Suporta links padrão (youtube.com/watch?v=...), encurtados (youtu.be/...),
    YouTube Shorts (youtube.com/shorts/...) e YouTube Music.
    """
    if not url or not isinstance(url, str):
        return False
    pattern = (
        r"^(https?://)?(www\.|m\.|music\.)?"
        r"(youtube\.com/(watch\?v=|shorts/|embed/)|youtu\.be/)"
        r"[\w-]{6,}(&\S*|\?\S*)?$"
    )
    return re.match(pattern, url.strip()) is not None


def sanitize_filename(name: str) -> str:
    """
    Remove caracteres inválidos para nomes de arquivos no Windows/Linux/macOS/Android.
    """
    cleaned = re.sub(r'[<>:"/\\|?*\x00-\x1F]', "_", name)
    cleaned = re.sub(r"\s+", " ", cleaned).strip().rstrip(".")
    return cleaned or "youtube_media"


def has_ffmpeg() -> bool:
    """Verifica se o executável do FFmpeg está disponível no PATH do sistema."""
    return shutil.which("ffmpeg") is not None


def parse_resolution_value(res: str) -> int:
    """Extrai o valor numérico de uma resolução (ex: '1080p' -> 1080) para ordenação."""
    if not res:
        return 0
    match = re.search(r"(\d+)", res)
    return int(match.group(1)) if match else 0


def parse_bitrate_value(abr: str) -> int:
    """Extrai o valor numérico de um bitrate (ex: '128kbps' -> 128) para ordenação."""
    if not abr:
        return 0
    match = re.search(r"(\d+)", abr)
    return int(match.group(1)) if match else 0


def extract_qualities(yt: YouTube) -> dict:
    """
    Analisa todas as streams disponíveis no objeto YouTube e organiza:
    - video_qualities: lista ordenada de resoluções MP4 (ex: 1080p, 720p, 480p, 360p...)
    - audio_qualities: lista ordenada de bitrates de áudio (ex: 160kbps, 128kbps, 70kbps, 48kbps...)
    """
    video_map = {}
    for stream in yt.streams.filter(file_extension="mp4"):
        res = getattr(stream, "resolution", None)
        if not res:
            continue
        is_prog = bool(getattr(stream, "is_progressive", False))
        filesize = getattr(stream, "filesize", 0) or getattr(stream, "filesize_approx", 0) or 0
        fps = getattr(stream, "fps", 30) or 30

        # Preferimos manter registro do stream progressivo se existir, ou o de maior qualidade
        existing = video_map.get(res)
        if existing is None or (is_prog and not existing["is_progressive"]):
            video_map[res] = {
                "id": res,
                "label": f"{res} ({fps}fps)",
                "detail": "Direto (Vídeo+Áudio)" if is_prog else "Alta Definição (FFmpeg)",
                "file_size_bytes": filesize,
                "is_progressive": is_prog,
            }

    video_qualities = sorted(
        video_map.values(),
        key=lambda item: parse_resolution_value(item["id"]),
        reverse=True,
    )

    audio_map = {}
    for stream in yt.streams.filter(only_audio=True):
        abr = getattr(stream, "abr", None)
        if not abr:
            continue
        filesize = getattr(stream, "filesize", 0) or getattr(stream, "filesize_approx", 0) or 0
        mime_type = getattr(stream, "mime_type", "audio/mp4")

        # Preferimos audio/mp4 quando houver empate de bitrate
        existing = audio_map.get(abr)
        if existing is None or ("mp4" in mime_type and "mp4" not in existing["detail"]):
            audio_map[abr] = {
                "id": abr,
                "label": f"MP3 {abr}",
                "detail": f"Fonte: {mime_type}",
                "file_size_bytes": filesize,
                "is_progressive": True,
            }

    audio_qualities = sorted(
        audio_map.values(),
        key=lambda item: parse_bitrate_value(item["id"]),
        reverse=True,
    )

    progressive_resolutions = sorted(
        [q["id"] for q in video_qualities if q["is_progressive"]],
        key=parse_resolution_value,
        reverse=True,
    )
    all_resolutions = [q["id"] for q in video_qualities]

    return {
        "progressive": progressive_resolutions,
        "all": all_resolutions,
        "video_qualities": video_qualities,
        "audio_qualities": audio_qualities,
    }


# ==============================================================================
# LÓGICA DE DOWNLOAD E CONVERSÃO (MP4 / MP3)
# ==============================================================================

def perform_download(
    url: str,
    extraction_format: str = "mp4",
    quality: str = "",
    output_dir: str = "",
):
    """
    Executa o download do vídeo (MP4) ou áudio (MP3) na qualidade e pasta escolhidas.

    Parâmetros:
    - url: Link do vídeo do YouTube.
    - extraction_format: 'mp4' (vídeo) ou 'mp3' (música/áudio).
    - quality: Resolução (ex: '720p', '360p') para MP4 ou bitrate (ex: '160kbps', '128kbps') para MP3.
    - output_dir: Caminho da pasta onde o arquivo final será salvo.

    Retorna:
    - (sucesso: bool, resultado_ou_erro: dict | str)
    """
    try:
        yt = YouTube(url)
        safe_title = sanitize_filename(yt.title)
        fmt = (extraction_format or "mp4").lower().strip()

        # Define a pasta de saída padrão caso não seja informada
        if not output_dir or not output_dir.strip():
            default_downloads = os.path.join(os.path.expanduser("~"), "Downloads")
            output_dir = default_downloads if os.path.isdir(default_downloads) else os.path.abspath("./downloads")

        output_dir = os.path.abspath(os.path.expanduser(output_dir.strip()))
        os.makedirs(output_dir, exist_ok=True)

        # ----------------------------------------------------------------------
        # MODO 1: EXTRAÇÃO DE ÁUDIO / MÚSICA (MP3)
        # ----------------------------------------------------------------------
        if fmt == "mp3":
            audio_streams = yt.streams.filter(only_audio=True)
            selected_stream = None

            if quality:
                selected_stream = audio_streams.filter(abr=quality).first()

            if selected_stream is None:
                # Seleciona o áudio de maior bitrate disponível
                selected_stream = audio_streams.order_by("abr").desc().first()

            if selected_stream is None:
                return False, "Nenhuma faixa de áudio encontrada para este vídeo."

            chosen_quality = getattr(selected_stream, "abr", quality or "128kbps")
            final_filename = f"{safe_title} [{chosen_quality}].mp3"
            final_path = os.path.join(output_dir, final_filename)

            with tempfile.TemporaryDirectory(prefix="gtv_audio_") as tmp_dir:
                temp_audio_path = selected_stream.download(
                    output_path=tmp_dir,
                    filename=f"raw_audio_{uuid.uuid4().hex}",
                )

                if has_ffmpeg():
                    # Converte para um arquivo MP3 real com o bitrate selecionado usando FFmpeg
                    bitrate_num = parse_bitrate_value(chosen_quality) or 128
                    cmd = [
                        "ffmpeg",
                        "-y",
                        "-i",
                        temp_audio_path,
                        "-vn",
                        "-ab",
                        f"{bitrate_num}k",
                        "-ar",
                        "44100",
                        final_path,
                    ]
                    proc = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
                    if proc.returncode != 0:
                        # Fallback caso ocorra erro na conversão: move o arquivo de áudio direto
                        shutil.copy2(temp_audio_path, final_path)
                else:
                    # Fallback caso FFmpeg não esteja instalado
                    shutil.copy2(temp_audio_path, final_path)

            return True, {
                "message": f"Áudio MP3 ({chosen_quality}) baixado com sucesso!",
                "file_path": final_path,
                "file_name": final_filename,
                "output_dir": output_dir,
                "format": "mp3",
                "quality": chosen_quality,
                "title": yt.title,
            }

        # ----------------------------------------------------------------------
        # MODO 2: EXTRAÇÃO DE VÍDEO (MP4)
        # ----------------------------------------------------------------------
        # 1. Tenta encontrar stream progressivo (vídeo + áudio juntos) na resolução pedida
        progressive_stream = None
        if quality:
            progressive_stream = yt.streams.filter(
                progressive=True, file_extension="mp4", resolution=quality
            ).first()

        if progressive_stream is not None:
            chosen_res = getattr(progressive_stream, "resolution", quality or "mp4")
            final_filename = f"{safe_title} [{chosen_res}].mp4"
            final_path = os.path.join(output_dir, final_filename)
            progressive_stream.download(output_path=output_dir, filename=final_filename)
            return True, {
                "message": f"Vídeo MP4 ({chosen_res}) baixado com sucesso!",
                "file_path": final_path,
                "file_name": final_filename,
                "output_dir": output_dir,
                "format": "mp4",
                "quality": chosen_res,
                "title": yt.title,
            }

        # 2. Procura stream adaptativo (DASH) na resolução pedida ou na melhor disponível
        video_stream = None
        if quality:
            video_stream = yt.streams.filter(file_extension="mp4", res=quality).first()

        if video_stream is None:
            # Se não informou qualidade ou não achou exata, pega a maior resolução MP4 disponível
            mp4_streams = [
                s for s in yt.streams.filter(file_extension="mp4") if getattr(s, "resolution", None)
            ]
            mp4_streams.sort(key=lambda s: parse_resolution_value(s.resolution), reverse=True)
            video_stream = mp4_streams[0] if mp4_streams else None

        if video_stream is None:
            return False, f"Vídeo na resolução '{quality}' não encontrado."

        chosen_res = getattr(video_stream, "resolution", quality or "mp4")
        final_filename = f"{safe_title} [{chosen_res}].mp4"
        final_path = os.path.join(output_dir, final_filename)

        # Se o stream já inclui faixa de áudio, baixa diretamente
        if getattr(video_stream, "includes_audio_track", False):
            video_stream.download(output_path=output_dir, filename=final_filename)
        else:
            # Stream adaptativo (apenas vídeo): baixa vídeo + áudio e une com FFmpeg
            audio_stream = (
                yt.streams.filter(only_audio=True, file_extension="mp4").order_by("abr").desc().first()
                or yt.streams.filter(only_audio=True).order_by("abr").desc().first()
            )

            if audio_stream and has_ffmpeg():
                with tempfile.TemporaryDirectory(prefix="gtv_video_") as tmp_dir:
                    v_path = video_stream.download(output_path=tmp_dir, filename="video_track.mp4")
                    a_path = audio_stream.download(output_path=tmp_dir, filename="audio_track")

                    cmd = [
                        "ffmpeg",
                        "-y",
                        "-i",
                        v_path,
                        "-i",
                        a_path,
                        "-c:v",
                        "copy",
                        "-c:a",
                        "aac",
                        "-shortest",
                        final_path,
                    ]
                    proc = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
                    if proc.returncode != 0:
                        shutil.copy2(v_path, final_path)
            else:
                video_stream.download(output_path=output_dir, filename=final_filename)

        return True, {
            "message": f"Vídeo MP4 ({chosen_res}) baixado com sucesso!",
            "file_path": final_path,
            "file_name": final_filename,
            "output_dir": output_dir,
            "format": "mp4",
            "quality": chosen_res,
            "title": yt.title,
        }

    except Exception as e:
        return False, str(e)


def get_video_info(url: str):
    """
    Obtém metadados completos do vídeo e as listas de qualidades de vídeo (MP4) e áudio (MP3).
    """
    try:
        yt = YouTube(url)
        qualities = extract_qualities(yt)
        publish_date_str = str(yt.publish_date) if yt.publish_date else ""

        video_info = {
            "video_id": yt.video_id,
            "title": yt.title or "Vídeo sem título",
            "author": yt.author or "Canal desconhecido",
            "length": yt.length or 0,
            "views": yt.views or 0,
            "description": yt.description or "",
            "publish_date": publish_date_str,
            "thumbnail_url": yt.thumbnail_url or "",
            "video_qualities": qualities["video_qualities"],
            "audio_qualities": qualities["audio_qualities"],
        }
        return video_info, None
    except Exception as e:
        return None, str(e)


# ==============================================================================
# ROTAS DA API FLASK
# ==============================================================================

@app.route("/health", methods=["GET"])
def health_check():
    """Retorna o status de funcionamento do servidor local e suporte a FFmpeg."""
    return jsonify({
        "status": "ok",
        "service": "GetThatVideo API (baseado em zararashraf/youtube-video-downloader-api)",
        "ffmpeg_available": has_ffmpeg(),
    }), 200


@app.route("/video_info", methods=["POST"])
def video_info_route():
    """Retorna informações detalhadas do vídeo e qualidades disponíveis."""
    data = request.get_json(silent=True) or {}
    url = data.get("url", "").strip()

    if not url:
        return jsonify({"error": "O parâmetro 'url' é obrigatório no corpo da requisição."}), 400

    if not is_valid_youtube_url(url):
        return jsonify({"error": "URL do YouTube inválida."}), 400

    info, error_message = get_video_info(url)
    if info:
        return jsonify(info), 200
    else:
        return jsonify({"error": error_message}), 500


@app.route("/available_resolutions", methods=["POST"])
def available_resolutions_route():
    """Retorna as resoluções de vídeo (MP4) e bitrates de áudio (MP3) disponíveis."""
    data = request.get_json(silent=True) or {}
    url = data.get("url", "").strip()

    if not url:
        return jsonify({"error": "O parâmetro 'url' é obrigatório no corpo da requisição."}), 400

    if not is_valid_youtube_url(url):
        return jsonify({"error": "URL do YouTube inválida."}), 400

    try:
        yt = YouTube(url)
        qualities = extract_qualities(yt)
        return jsonify(qualities), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500


@app.route("/download/<resolution>", methods=["POST"])
def download_by_resolution(resolution: str):
    """
    Endpoint original do repositório `zararashraf/youtube-video-downloader-api`,
    mantido para total compatibilidade e atualizado para suportar pasta customizada.
    """
    data = request.get_json(silent=True) or {}
    url = data.get("url", "").strip()
    output_dir = data.get("output_dir", "").strip()
    fmt = data.get("format", "mp4").strip()

    if not url:
        return jsonify({"error": "Missing 'url' parameter in the request body."}), 400

    if not is_valid_youtube_url(url):
        return jsonify({"error": "Invalid YouTube URL."}), 400

    success, result = perform_download(
        url=url,
        extraction_format=fmt,
        quality=resolution,
        output_dir=output_dir,
    )

    if success:
        return jsonify(result), 200
    else:
        return jsonify({"error": result}), 500


@app.route("/download", methods=["POST"])
def download_media():
    """
    Endpoint principal utilizado pelo app Kotlin Multiplatform.
    Aceita JSON:
    {
        "url": "https://www.youtube.com/watch?v=...",
        "format": "mp4" | "mp3",
        "quality": "720p" | "128kbps",
        "output_dir": "C:/Users/.../Downloads"
    }
    """
    data = request.get_json(silent=True) or {}
    url = data.get("url", "").strip()
    extraction_format = data.get("format", "mp4").strip().lower()
    quality = data.get("quality", "").strip()
    output_dir = data.get("output_dir", "").strip()

    if not url:
        return jsonify({"error": "Informe a URL do vídeo do YouTube."}), 400

    if not is_valid_youtube_url(url):
        return jsonify({"error": "A URL informada não é um link válido do YouTube."}), 400

    success, result = perform_download(
        url=url,
        extraction_format=extraction_format,
        quality=quality,
        output_dir=output_dir,
    )

    if success:
        return jsonify(result), 200
    else:
        return jsonify({"error": result}), 500


@app.route("/download_stream", methods=["POST"])
def download_stream():
    """
    Faz o download/conversão em diretório temporário e transmite o arquivo binário
    para o cliente (útil quando o app roda em um dispositivo Android conectado à API).
    """
    data = request.get_json(silent=True) or {}
    url = data.get("url", "").strip()
    extraction_format = data.get("format", "mp4").strip().lower()
    quality = data.get("quality", "").strip()

    if not url or not is_valid_youtube_url(url):
        return jsonify({"error": "URL do YouTube inválida."}), 400

    temp_out = tempfile.mkdtemp(prefix="gtv_stream_")
    success, result = perform_download(
        url=url,
        extraction_format=extraction_format,
        quality=quality,
        output_dir=temp_out,
    )

    if not success:
        shutil.rmtree(temp_out, ignore_errors=True)
        return jsonify({"error": result}), 500

    file_path = result["file_path"]
    file_name = result["file_name"]
    mimetype = "audio/mpeg" if extraction_format == "mp3" else "video/mp4"

    return send_file(
        file_path,
        mimetype=mimetype,
        as_attachment=True,
        download_name=file_name,
    )


if __name__ == "__main__":
    # Executa na porta 5000 (acessível localmente em http://127.0.0.1:5000)
    app.run(host="0.0.0.0", port=5000, debug=False)
