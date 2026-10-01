from PIL import Image, ImageDraw, ImageFilter
S = 4  # supersample
W = 400 * S
img = Image.new("RGBA", (W, W), (0, 0, 0, 0))
d = ImageDraw.Draw(img)
# rounded-square background with a soft vertical gradient (slate -> deep teal)
bg = Image.new("RGBA", (W, W))
for y in range(W):
    t = y / (W - 1)
    r = int(28 + (16 - 28) * t); g = int(40 + (58 - 40) * t); b = int(52 + (66 - 52) * t)
    ImageDraw.Draw(bg).line([(0, y), (W, y)], fill=(r, g, b, 255))
mask = Image.new("L", (W, W), 0)
ImageDraw.Draw(mask).rounded_rectangle([0, 0, W - 1, W - 1], radius=70 * S, fill=255)
img.paste(bg, (0, 0), mask)
d = ImageDraw.Draw(img)
# the record
cx, cy, R = 188 * S, 196 * S, 150 * S
d.ellipse([cx - R, cy - R, cx + R, cy + R], fill=(14, 16, 20, 255))
for i, rr in enumerate(range(R - 10 * S, 58 * S, -9 * S)):
    shade = 34 if i % 2 == 0 else 26
    d.ellipse([cx - rr, cy - rr, cx + rr, cy + rr], outline=(shade, shade + 2, shade + 6, 255), width=2 * S)
# sheen
sheen = Image.new("RGBA", (W, W), (0, 0, 0, 0))
ImageDraw.Draw(sheen).pieslice([cx - R, cy - R, cx + R, cy + R], 205, 238, fill=(255, 255, 255, 14))
ImageDraw.Draw(sheen).pieslice([cx - R, cy - R, cx + R, cy + R], 25, 58, fill=(255, 255, 255, 9))
img = Image.alpha_composite(img, sheen)
d = ImageDraw.Draw(img)
# label
L = 54 * S
d.ellipse([cx - L, cy - L, cx + L, cy + L], fill=(240, 176, 64, 255))
d.ellipse([cx - L + 8 * S, cy - L + 8 * S, cx + L - 8 * S, cy + L - 8 * S], outline=(214, 140, 40, 255), width=3 * S)
h = 9 * S
d.ellipse([cx - h, cy - h, cx + h, cy + h], fill=(14, 16, 20, 255))
# approval badge (bottom right): ring + green disc + white check
bx, by, B = 300 * S, 300 * S, 74 * S
shadow = Image.new("RGBA", (W, W), (0, 0, 0, 0))
ImageDraw.Draw(shadow).ellipse([bx - B + 6 * S, by - B + 10 * S, bx + B + 6 * S, by + B + 10 * S], fill=(0, 0, 0, 110))
shadow = shadow.filter(ImageFilter.GaussianBlur(10 * S))
img = Image.alpha_composite(img, shadow)
d = ImageDraw.Draw(img)
d.ellipse([bx - B, by - B, bx + B, by + B], fill=(236, 244, 240, 255))
B2 = B - 9 * S
d.ellipse([bx - B2, by - B2, bx + B2, by + B2], fill=(46, 170, 96, 255))
pts = [(bx - 32 * S, by + 2 * S), (bx - 9 * S, by + 26 * S), (bx + 36 * S, by - 24 * S)]
d.line(pts, fill=(255, 255, 255, 255), width=17 * S, joint="curve")
for p in (pts[0], pts[2]):
    r = 8 * S
    d.ellipse([p[0] - r, p[1] - r, p[0] + r, p[1] + r], fill=(255, 255, 255, 255))
out = img.resize((400, 400), Image.LANCZOS)
out.save("etchgate-logo-400.png")
out.resize((128, 128), Image.LANCZOS).save("etchgate_logo.png")
print("ok")
