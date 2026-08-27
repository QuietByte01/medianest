import os

files_to_fix = [
    'app/src/main/java/com/medianest/util/MediaAnalyzer.kt',
    'app/src/main/java/com/medianest/util/MediaProcessorEngine.kt',
    'app/src/main/java/com/medianest/util/VideoColorizerEngine.kt'
]

for file_path in files_to_fix:
    with open(file_path, 'r') as f:
        content = f.read()
    
    # We want to replace the sequence:
    # decoder.start()
    # ... loop ...
    # try { decoder.stop() } catch (e: Exception) {}
    # try { decoder.release() } catch (e: Exception) {}
    # break
    
    # Let's just do a simple string replace for the start of the block and the end.
    if 'val decoder = MediaCodec.createDecoderByType(mime)' in content and 'decoder.configure(format, null, null, 0)' in content:
        content = content.replace(
            'decoder.configure(format, null, null, 0)\n                decoder.start()',
            'decoder.configure(format, null, null, 0)\n                decoder.start()\n                try {'
        )
        content = content.replace(
            'try { decoder.stop() } catch (e: Exception) {}\n                try { decoder.release() } catch (e: Exception) {}\n                break\n            }',
            '}\n                finally {\n                    try { decoder.stop() } catch (e: Exception) {}\n                    try { decoder.release() } catch (e: Exception) {}\n                }\n                break\n            }'
        )
        with open(file_path, 'w') as f:
            f.write(content)

