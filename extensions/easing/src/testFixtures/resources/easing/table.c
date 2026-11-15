#include <stdio.h>
#include <math.h>
#include "easing.h"
struct {const char*n; AHEasingFunction f; int libm;} fs[] = {
{"linear",LinearInterpolation,0},{"in-quad",QuadraticEaseIn,0},{"in-cubic",CubicEaseIn,0},{"in-quart",QuarticEaseIn,0},{"in-quint",QuinticEaseIn,0},{"in-sine",SineEaseIn,1},{"in-circ",CircularEaseIn,0},{"in-expo",ExponentialEaseIn,1},{"in-elastic",ElasticEaseIn,1},{"in-back",BackEaseIn,1},{"in-bounce",BounceEaseIn,0},
{"out-quad",QuadraticEaseOut,0},{"out-cubic",CubicEaseOut,0},{"out-quart",QuarticEaseOut,0},{"out-quint",QuinticEaseOut,0},{"out-sine",SineEaseOut,1},{"out-circ",CircularEaseOut,0},{"out-expo",ExponentialEaseOut,1},{"out-elastic",ElasticEaseOut,1},{"out-back",BackEaseOut,1},{"out-bounce",BounceEaseOut,0},
{"in-out-quad",QuadraticEaseInOut,0},{"in-out-cubic",CubicEaseInOut,0},{"in-out-quart",QuarticEaseInOut,0},{"in-out-sine",SineEaseInOut,1},{"in-out-circ",CircularEaseInOut,0},{"in-out-expo",ExponentialEaseInOut,1},{"in-out-elastic",ElasticEaseInOut,1},{"in-out-back",BackEaseInOut,1},{"in-out-bounce",BounceEaseInOut,0}};
int main(void){
  double xs[]={0.0,0.3,nextafter(0.5,0),0.5,0.75,1.0};
  double bp[]={4/11.0,8/11.0,0.9};
  printf("curve,x,glibc answers,arithmetic alone\n");
  for(int i=0;i<30;i++){
    for(int k=0;k<6;k++) printf("%s,%.17g,%.17g,%s\n",fs[i].n,xs[k],fs[i].f(xs[k]),fs[i].libm?"false":"true");
    if(i==20) for(int k=0;k<3;k++){ printf("%s,%.17g,%.17g,true\n",fs[i].n,nextafter(bp[k],0),fs[i].f(nextafter(bp[k],0))); printf("%s,%.17g,%.17g,true\n",fs[i].n,bp[k],fs[i].f(bp[k])); }
  }
}
