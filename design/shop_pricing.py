"""Stable shop prices shared by asset generators: coins in steps of five."""
def shop_price(kind, width, height, depth):
    if kind == 'frame': return 10
    if kind in ('chair', 'sofa'): return 30 if width >= .75 or height >= 1 else 25
    if kind == 'shelf': return 30 if height >= 1 else 25
    if kind == 'table': return 25 if width >= .65 or width * depth >= .25 else 20
    if kind == 'rug': return 25
    if kind == 'lamp': return 20 if height >= .65 else 15
    if kind in ('sculpt', 'sculpture'): return 20
    return 15
