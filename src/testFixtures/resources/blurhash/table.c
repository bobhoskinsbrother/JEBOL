#include <stdio.h>
#include <stdint.h>
#include <string.h>
#include "encode.h"
#include "decode.h"
static uint32_t seed;
static int next_byte(void){ seed = seed*1103515245u + 12345u; return (seed >> 16) & 0xFF; }
static void fill(uint8_t *p, int w, int h, int pattern){
  seed = (uint32_t)(w*131 + h*7 + pattern);
  for(int y=0;y<h;y++) for(int x=0;x<w;x++){ uint8_t *q=p+4*(x+y*w);
    switch(pattern){
      case 0: q[0]=next_byte(); q[1]=next_byte(); q[2]=next_byte(); q[3]=next_byte(); break;
      case 1: q[0]=w>1?x*255/(w-1):0; q[1]=h>1?y*255/(h-1):0; q[2]=(x+y)&255; q[3]=255; break;
      case 2: q[0]=q[1]=q[2]=0; q[3]=255; break;
      case 3: q[0]=q[1]=q[2]=255; q[3]=255; break;
      case 4: q[0]=200; q[1]=10; q[2]=60; q[3]=0; break;
      default: q[0]=(x*37)&255; q[1]=(y*91)&255; q[2]=((x^y)*13)&255; q[3]=128; } } }
static uint64_t fnv(const uint8_t *p, int n){ uint64_t h=0xcbf29ce484222325ull; for(int i=0;i<n;i++){ h^=p[i]; h*=0x100000001b3ull; } return h; }
int main(void){
  static uint8_t img[100*100*4]; static char hashes[4000][200]; static int is3x3[4000]; int nh=0;
  int sizes[][2]={{0,0},{1,1},{1,7},{7,1},{2,2},{3,3},{5,4},{16,16},{32,17},{64,64},{100,37}};
  FILE *e=fopen("blurhash-encode.tsv","w"); fprintf(e,"width\theight\tpattern\tx components\ty components\thash\n");
  for(int s=0;s<11;s++) for(int p=0;p<6;p++){ int w=sizes[s][0],h=sizes[s][1]; fill(img,w,h,p);
    for(int xc=1;xc<=9;xc++) for(int yc=1;yc<=9;yc++){
      if(!(xc==3&&yc==3) && (s%3!=0)) continue;
      const char *hs=blurHashForPixels(xc,yc,w,h,img,4*w);
      fprintf(e,"%d\t%d\t%d\t%d\t%d\t%s\n",w,h,p,xc,yc,hs); is3x3[nh]=(xc==3&&yc==3); strcpy(hashes[nh++],hs); } }
  fclose(e);
  const char *bad[]={"","LEHV6","LEHV6nWB2yk8pyo0adR*.7kCMdnj!","LEHV6nWB2yk8pyo0adR*.7kCMdn","~~~~~~","00000000","L~HV6nWB2yk8pyo0adR*.7kCMdnj","LEHV6nWB2yk8pyo0adR*.7kCMdnj"};
  for(int i=0;i<8;i++) strcpy(hashes[nh++],bad[i]);
  int ds[][2]={{0,0},{1,1},{3,2},{32,32},{64,48}}; int punches[]={-1,0,1,2,5};
  static uint8_t out[64*48*4];
  FILE *d=fopen("blurhash-decode.tsv","w"); fprintf(d,"hash row\twidth\theight\tpunch\tdecoded\tpixel digest\n");
  for(int i=0;i<nh;i++) for(int k=0;k<5;k++) for(int s=0;s<5;s++){
    int every=(i>=nh-8)||is3x3[i]; if(!every && !(k==2&&s==3)) continue; if(every && k!=2 && s!=3) continue;
    int w=ds[s][0],h=ds[s][1]; memset(out,7,sizeof out);
    int r=decodeToArray(hashes[i],w,h,punches[k],4,out);
    fprintf(d,"%d\t%d\t%d\t%d\t%s\t%016llx\n",i+1,w,h,punches[k],r==0?"true":"false",(unsigned long long)fnv(out,w*h*4)); }
  fclose(d);
}
