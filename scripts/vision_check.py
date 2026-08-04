# -*- coding: utf-8 -*-
"""调用 LM Studio 本地视觉模型（Qwen3VL 8B）描述图片，用于主模型非多模态时的看图验证。
用法：python vision_check.py <image_path> [question]
"""
import base64
import json
import sys
import urllib.request

BASE = 'http://127.0.0.1:1234'
API_KEY = 'sk-lm-fqeNTolu:T7TcFzlAT3iF0hO1P3FB'
MODEL = 'ds'


def http_json(method, path, data=None, headers=None):
    req = urllib.request.Request(BASE + path, method=method)
    req.add_header('Authorization', f'Bearer {API_KEY}')
    req.add_header('Content-Type', 'application/json')
    for k, v in (headers or {}).items():
        req.add_header(k, v)
    body = json.dumps(data).encode() if data is not None else None
    with urllib.request.urlopen(req, body) as r:
        return json.loads(r.read().decode())


def loaded_instances():
    try:
        r = http_json('GET', '/v1/models')
        return [m['id'] for m in r.get('data', [])], None
    except Exception as e:
        return [], str(e)


def main():
    img_path = sys.argv[1]
    question = sys.argv[2] if len(sys.argv) > 2 else '请描述这张图片的内容和视觉效果。'

    # 检查模型是否已加载
    ids, err = loaded_instances()
    print('loaded models:', ids, err or '')
    loaded_by_us = False
    if MODEL not in ids:
        print('loading model...')
        try:
            http_json('POST', '/api/v1/models/load', {'model': MODEL})
            loaded_by_us = True
            import time
            time.sleep(3)
        except Exception as e:
            print('load error:', e)
            sys.exit(1)

    with open(img_path, 'rb') as f:
        b64 = base64.b64encode(f.read()).decode()

    payload = {
        'model': MODEL,
        'messages': [{
            'role': 'user',
            'content': [
                {'type': 'image_url', 'image_url': {'url': f'data:image/png;base64,{b64}'}},
                {'type': 'text', 'text': question},
            ],
        }],
        'max_tokens': 600,
    }
    try:
        r = http_json('POST', '/v1/chat/completions', payload)
        print('--- VISION REPLY ---')
        print(r['choices'][0]['message']['content'])
    except Exception as e:
        print('vision error:', e)
        sys.exit(1)
    finally:
        if loaded_by_us:
            try:
                http_json('POST', '/api/v1/models/unload', {'instance_id': ''})
                print('(model unloaded by us)')
            except Exception:
                pass


if __name__ == '__main__':
    main()
