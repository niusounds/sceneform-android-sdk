#pragma version(1)
#pragma rs java_package_name(com.niusounds.scrrcv.ar)
#pragma rs_fp_relaxed

float3 keyColor;
float threshold;
float blendThreshold;

uchar4 RS_KERNEL chromaKey(uchar4 in, uint32_t x, uint32_t y) {
  float3 color = rsUnpackColor8888(in).rgb;
  float dist = distance(color, keyColor);

  if (dist < threshold) {
    uchar4 out = {0, 0, 0, 0};
    return out;
  } else if (dist < blendThreshold && threshold < blendThreshold) {
    float alpha = (dist - threshold) / (blendThreshold - threshold);

    // Modified alpha blending
    float3 resultColor = color - keyColor * (1.0 - alpha);
    //return rsPackColorTo8888(resultColor); // good quality?
    return rsPackColorTo8888(resultColor.r, resultColor.g, resultColor.b, alpha); // better quality?

    // Simple alpha blending (bad quality)
    // uchar4 out = in;
    // out.a = out.a * alpha;
    // return out;
  }
  return in;
}
