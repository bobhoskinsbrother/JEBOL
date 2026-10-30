#include <stdio.h>
#include <string.h>
#include <stdint.h>
#include <math.h>
#include "easing.h"
static AHEasingFunction fs[] = {LinearInterpolation, QuadraticEaseIn, CubicEaseIn, QuarticEaseIn, QuinticEaseIn, SineEaseIn, CircularEaseIn, ExponentialEaseIn, ElasticEaseIn, BackEaseIn, BounceEaseIn,
 QuadraticEaseOut, CubicEaseOut, QuarticEaseOut, QuinticEaseOut, SineEaseOut, CircularEaseOut, ExponentialEaseOut, ElasticEaseOut, BackEaseOut, BounceEaseOut,
 QuadraticEaseInOut, CubicEaseInOut, QuarticEaseInOut, SineEaseInOut, CircularEaseInOut, ExponentialEaseInOut, ElasticEaseInOut, BackEaseInOut, BounceEaseInOut};
static void out(double v){uint64_t b; memcpy(&b,&v,8); printf(" %016llx",(unsigned long long)b);}
int main(void){
  double sp[]={0.0,-0.0,1.0,0.5,4/11.0,8/11.0,0.9,0.25,0.75,-1.0,2.0,-0.5,1.5,1e-300,0.1,0.2,0.3};
  int n=sizeof sp/sizeof sp[0];
  for(int f=0;f<30;f++){ printf("%d",f);
    for(int i=0;i<n;i++){ out(fs[f](sp[i])); out(fs[f](nextafter(sp[i],-1e9))); out(fs[f](nextafter(sp[i],1e9))); }
    for(int i=0;i<=65536;i++) out(fs[f](i/65536.0));
    printf("\n"); }
}
